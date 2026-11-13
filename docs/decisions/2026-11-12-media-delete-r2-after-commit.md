# 미디어 삭제·정리 배치의 R2 삭제는 DB 전이 커밋 뒤, 락 밖에서 한다

- **날짜**: 2026-11-12
- **관련 이슈**: #180
- **상태**: 결정됨 (2026-11-12 사람이 결정)
- **계약**: [2026-11-12-media-delete-r2-outside-lock](../contracts/2026-11-12-media-delete-r2-outside-lock.md)

## 상황 (Context)
- [외부 API 호출은 트랜잭션·락 밖에서 실행](2026-11-05-external-call-outside-transaction.md)(#161)에서 업로드 완료의 R2 `headObject`는 락 밖으로 뺐지만, `MediaService.deleteUnusedMedia`와 정리 배치(`deleteExpired{Pending,Uploaded,Deleted}Media`)는 media 행에 PESSIMISTIC_WRITE 락을 잡은 채 R2 `headObject`(UPLOADED만)와 `deleteObject`를 호출했다.
- 순서도 "R2 삭제 → 커밋"이었다. 커밋이 실패하면 R2 오브젝트는 이미 없는데 DB는 PENDING/UPLOADED로 롤백됐고, UPLOADED라면 이후 피드에 붙어 깨진 이미지가 될 수 있었다.
- UPLOADED 삭제 전 `headObject`가 404면 MEDIA010으로 롤백했다. 그래서 R2에 오브젝트가 없는 UPLOADED media는 사용자가 지울 수 없었고, 만료 UPLOADED 배치에서는 매일 실패하며 영원히 UPLOADED로 남았다.

## 검토한 선택지 (Options considered)
1. **락 밖 분리, DB 먼저**: 락 트랜잭션에서 재검증 후 DELETED로 바꾸고 key만 돌려줌 → 커밋 → 트랜잭션 밖에서 R2 삭제(404 성공). R2 실패는 로그만 남기고, 하루 뒤 DELETED 하드 삭제 배치가 다시 지운다.
2. **아웃박스(`ObjectDeleteOutbox*`)**: DELETED 전이와 함께 아웃박스 행을 저장하고 afterCommit에서 처리.
3. **지연 삭제만**: DELETED로만 바꾸고 R2는 하드 삭제 배치에 맡김.
4. (기각) **락 밖 분리, R2 먼저**: 되돌릴 수 없는 삭제가 먼저 일어나고, 그 사이 media가 USED가 되면 이미지가 깨진 채 남는다.

## 결정 (Decision)
1번. 락이 필요한 DB 단계는 새 빈 `MediaDeletionService`(#161과 같은 별도 빈 방식)로 옮기고, `MediaService`는 트랜잭션 없이 "DB 전이 커밋 → R2 삭제" 순서만 조율한다. R2 삭제 전 `headObject`는 없애고, delete 404는 상태와 상관없이 성공으로 본다.

| 경로 | 순서 | R2 실패 시 |
|---|---|---|
| `deleteUnusedMedia` | 락 + 검증(MEDIA009 → 008 → 007) + DELETED 커밋 → R2 delete | 로그만 남기고 200 |
| 만료 PENDING/UPLOADED | 락 + 상태 재확인 + DELETED 커밋 → R2 delete | 로그만 남기고 true |
| 만료 DELETED 하드 삭제 | 락 없이 조회 → R2 delete → 락 + DELETED 재확인 후 행 삭제 | MEDIA010, 행을 남겨 다음 날 재시도 |

## 이유 (Rationale)
- 기존 코드가 이미 "DB에서 DELETED로 바꾸고 R2는 하드 삭제 배치가 지운다"는 지연 삭제 체인을 쓰고 있다(피드·데일리 질문·팀 멤버의 `markDeleted`). R2 삭제가 실패해도 이 체인이 안전망이 되므로 새 저장소가 필요 없다.
- 2번은 `ObjectDeleteOutboxProcessor`가 `REQUIRES_NEW` 트랜잭션 안에서 R2를 호출하므로 Processor까지 고쳐야 커넥션을 잡지 않는다. 재시도도 5회 후 EXHAUSTED로 끝나 지금(매일 무한 재시도)보다 약해지고, soft delete 경로에서는 DELETED 행 자체가 이미 영속적인 삭제 요청 역할을 해서 중복이다.
- 3번은 가장 단순하지만 사용자가 지운 UPLOADED 오브젝트가 public URL로 1~2일 더 남는다.
- 하드 삭제만 순서가 다른 이유: 행을 먼저 지우면 R2 실패 시 다시 지울 근거(키)가 사라진다. R2를 먼저 지우고 락 단계에서 DELETED를 재확인하면, R2 실패 시 행이 남아 다음 날 다시 시도한다.

## 의도된 동작 변경
계약의 ["의도된 동작 변경" 표](../contracts/2026-11-12-media-delete-r2-outside-lock.md#의도된-동작-변경-2026-11-12-사용자-허용)를 따른다. 요약: 삭제 API에서 R2 실패·s3Client 없음·UPLOADED 오브젝트 없음(head 404, delete 404)이 MEDIA010 → 200, 만료 PENDING/UPLOADED 배치는 R2 실패에도 DELETED 커밋, 커밋 실패 시 R2를 호출하지 않음. 트랜잭션 시작·커밋 실패 응답은 COMMON004 그대로.

## 트레이드오프 / 남은 리스크
- R2 삭제가 실패하면 오브젝트가 하드 삭제 배치(DELETED 보관 1일, 매일 04시)까지 public URL로 남는다.
- 하드 삭제 경로에서 R2 삭제와 행 삭제 사이에는 락이 없다. 그 사이 DELETED가 아니게 되면 행은 지우지 않지만 오브젝트는 이미 없다. DELETED → 다른 상태 전이는 초대 수락 프로필 경로의 상태 검증 누락(#191)에서만 생기며, 그 버그는 분리 전에도 DELETED media를 되살릴 수 있었다.
- 만료 PENDING/UPLOADED 배치의 삭제 건수는 이제 "DB 전이 건수"다. R2 삭제가 실패한 건도 포함된다.
- R2 `S3Client` 타임아웃은 아직 없다(#179). 커넥션은 잡지 않지만 요청·배치 자체는 R2 응답만큼 길어질 수 있다.
- 커넥션 미점유 테스트(`ExternalCallConnectionTest`)의 Hikari `activeConnections`는 풀 전체 값이라, 테스트 중 30분 주기 아웃박스 재시도 배치가 돌면 드물게 흔들릴 수 있다(기존 테스트와 같은 위험).
