# 작업 계약: 미디어 삭제·정리 배치의 R2 호출을 락 밖으로 분리

- **관련 이슈**: #180
- **브랜치**: `Refactor/#180-media-r2-outside-lock` (Orca worktree, develop 147cb7d 기준)
- **상태**: 완료 (2026-11-12, PR #192)

## 목표
- `deleteUnusedMedia`와 정리 배치(`deleteExpired{Pending,Uploaded,Deleted}Media`)에서 R2 `headObject`/`deleteObject`가 DB 트랜잭션·PESSIMISTIC_WRITE 락 밖에서 실행되게 한다.
- 순서는 **DB 전이 커밋 → R2 삭제**로 한다. 지금은 R2를 먼저 지우고 커밋하므로, 커밋에 실패하면 오브젝트는 없는데 DB는 PENDING/UPLOADED로 남는 구멍이 있다.

## 설계 (2026-11-12 사용자 선택: A안)
- #161 패턴을 따른다. 트랜잭션 구간은 새 빈 `service/media/MediaDeletionService`(가칭)로 빼고, `MediaService`는 트랜잭션 없이 "DB → R2" 순서만 조율한다. self-invocation과 `TransactionTemplate`은 쓰지 않는다.
- 새 빈은 엔티티 대신 R2 삭제에 필요한 값(mediaId, publicId, key)만 record로 돌려준다.
- head는 없앤다. R2 delete는 404를 성공으로 본다(이제 모든 상태에서 같다).

| 경로 | 이후 흐름 |
|---|---|
| `deleteUnusedMedia` | 새 빈 `@Transactional`: `findByPublicIdForUpdate` → 지금과 같은 검증 순서(없음/DELETED MEDIA009 → 소유자 MEDIA008 → USED MEDIA007) → DELETED로 바꾸고 key 반환 → 커밋 → 트랜잭션 밖에서 R2 delete. R2 실패나 s3Client 없음은 `log.error`/`warn`만 남기고 200. 오브젝트는 하루 뒤 DELETED 하드 삭제 배치가 다시 지운다 |
| 만료 PENDING/UPLOADED 배치 | 새 빈 `@Transactional`: `findByIdForUpdate` → 상태가 다르면 빈 결과(false) → DELETED로 바꾸고 key 반환 → 커밋 → 트랜잭션 밖에서 R2 delete. R2 실패는 로그만 남기고 `true`를 반환한다(DB 전이는 완료) |
| 만료 DELETED 하드 삭제 배치 | 락 없이 `findById`(트랜잭션 없음) → 없거나 DELETED가 아니면 false → R2 delete(트랜잭션 밖, 404 무시. 그 밖의 실패는 MEDIA010을 던지고, 배치가 로그를 남긴 뒤 다음 날 다시 시도한다) → 새 빈 `@Transactional`: `findByIdForUpdate` → DELETED인지 다시 확인 → 행 삭제 |

## 의도된 동작 변경 (2026-11-12 사용자 허용)

`DELETE /api/v1/media/{id}` (호출부: `MediaController` 1곳)

| 경우 | 이전 | 이후 |
|---|---|---|
| PENDING, R2 delete 실패(404 외) 또는 s3Client 없음 | MEDIA010 500, 롤백(PENDING 유지) | 200, DELETED 커밋, 에러 로그. R2는 하드 삭제 배치가 다시 지움 |
| UPLOADED, R2에 오브젝트 없음(head 404) | MEDIA010 500, 롤백 | 200, DELETED |
| UPLOADED, head 성공 후 delete 404 | MEDIA010 500, 롤백 | 200, DELETED (2026-11-12 verifier 지적으로 표 보완) |
| UPLOADED, head·delete 실패(404 외) 또는 s3Client 없음 | MEDIA010 500, 롤백 | 200, DELETED 커밋, 에러 로그 |
| 트랜잭션 시작 실패 / 락 대기 실패 | COMMON004 500, R2 호출 없음 | 같음 |
| 커밋 실패 | COMMON004 500, **R2는 이미 지워졌는데 DB는 롤백됨** | COMMON004 500, R2 호출 없음 |
| 정상, 없는 media, DELETED, 소유자 아님, USED, UUID 형식 오류 | 200 / MEDIA009 / MEDIA009 / MEDIA008 / MEDIA007 / COMMON001 | 같음 |

정리 배치 (`MediaCleanupBatchService`, 건별로 `catch(Exception)` 후 로그 → 다음 건)

| 경우 | 이전 | 이후 |
|---|---|---|
| 만료 PENDING/UPLOADED, R2 실패(head 성공 후 delete 404, s3Client 없음 포함) | 롤백, 다음 날 같은 상태로 재시도. 만료 UPLOADED에서 오브젝트가 없으면(head 404) **영원히 실패** | DELETED 커밋, 로그, 하드 삭제 배치(1일 후)가 R2를 다시 지움. 삭제 카운트에 포함 |
| 만료 PENDING/UPLOADED, 트랜잭션 시작·커밋 실패 | 배치가 로그. 커밋 실패 시 R2는 이미 지워짐 | 배치가 로그. R2 호출 없음 |
| 만료 DELETED, R2 실패 | 롤백, 다음 날 재시도 | 같음(행 삭제 전에 R2를 호출하고, 실패하면 행을 남김) |
| 만료 DELETED, R2 삭제 후 행 삭제 트랜잭션의 시작·커밋 실패 | (R2가 락 안이었고, 커밋 실패 시 R2만 지워지고 행은 남음) | 같은 결과(R2만 지워지고 행은 남음, 다음 날 404 무시 후 행 삭제) |
| 만료 DELETED, R2 삭제와 행 삭제 사이에 DELETED가 아니게 됨 | 락으로 막혀서 일어나지 않음 | 행은 삭제하지 않고 false. 이 전이는 #191 버그 경로(`TeamInvitationService`)에서만 생김 |

- 업로드 완료(`MediaUploadCompletionService.complete`)와의 경쟁은 그대로다. 같은 media 행 락으로 직렬화하고, 늦은 쪽이 상태를 다시 확인한다(complete: PENDING 아니면 MEDIA014, delete: DELETED면 MEDIA009, 배치: 상태 다르면 false).

## 범위: 건드려도 되는 파일
- main
  - `service/media/MediaService.java`
  - `service/media/MediaDeletionService.java`(신규), R2 삭제 대상 record 1개(신규, 같은 패키지)
- test
  - `service/media/MediaServiceTest.java`, `service/media/MediaDeletionServiceTest.java`(신규)
  - `service/media/MediaCleanupBatchServiceTest.java`(필요할 때만)
  - `service/ExternalCallConnectionTest.java`
- docs
  - `docs/decisions/2026-11-12-media-delete-r2-after-commit.md`(신규), `docs/decisions/README.md`
  - `docs/decisions/2026-11-05-external-call-outside-transaction.md`: "남은 리스크"의 후속 이슈 줄에 #180으로 해소됐다고 표시만 한다

## 완료 조건
- [ ] `./gradlew test`(ArchUnit 포함)와 `./gradlew spotlessCheck` 통과
- [ ] `MediaService`의 `deleteUnusedMedia`, `deleteExpired{Pending,Uploaded,Deleted}Media`에 `@Transactional`이 없다. 락 조회(`find*ForUpdate`)는 새 빈에서만 한다. `headObjectForDelete`는 삭제됐다
- [ ] 커넥션 미점유 테스트(`ExternalCallConnectionTest`): `deleteUnusedMedia`, 만료 PENDING, 만료 UPLOADED, 만료 DELETED 네 경로에서 `S3Client.deleteObject` answer 안에서 트랜잭션 비활성과 Hikari `activeConnections == 0`을 캡처해 answer 밖에서 단언한다. answer가 실제로 호출됐는지 verify하고, `headObject`는 `never()`. DB 결과도 확인한다(DELETED 전이, 하드 삭제 후 행 없음). 필요하면 `cleanUp`에 아웃박스 정리를 추가한다
- [ ] 단위 테스트
  - `deleteUnusedMedia`: R2 실패, s3Client 없음, UPLOADED 404에서도 예외 없이 끝나고 DELETED 전이 빈 메서드가 호출된다
  - 검증 실패(MEDIA009/008/007)면 R2를 호출하지 않는다(`never()`)
  - 전이 빈이 예외를 던지면(커밋 실패 가정) R2를 호출하지 않는다(`never()`)
  - 만료 PENDING/UPLOADED: 상태가 다르면 R2 `never()`, R2가 실패해도 true
  - 만료 DELETED: R2 실패(404 외)면 MEDIA010이고 행 삭제 빈 `never()`. 404면 행을 삭제한다. 락 단계에서 DELETED가 아니면 행을 삭제하지 않는다
  - 새 빈: 검증 순서와 에러 코드가 기존과 같다
- [ ] 위 "의도된 동작 변경" 표에 없는 응답·DB 결과 변경이 없다. 바뀐 기존 테스트 기대값은 모두 표의 행에 대응한다
- [ ] 결정 기록에 쓴 수치·전칭 주장은 grep으로 다시 센다

## 커밋 구성 (한 PR, 각 커밋 승인)
1. `MediaDeletionService` 추가, `MediaService` 삭제·배치 경로 분리, 단위 테스트
2. `ExternalCallConnectionTest`에 삭제·배치 경로 추가
3. 결정 기록, README 인덱스, #161 결정 기록의 후속 줄 갱신

## 하지 말 것
- 아웃박스(`ObjectDeleteOutbox*`) 사용 또는 수정, 스키마·마이그레이션 변경, `application*.yaml` 변경
- `TeamInvitationService` 수정(#191), S3Client 타임아웃(#179)
- `MediaCleanupBatchService`의 주기·보관 기간·건수 변경
- `completeUpload`/`MediaUploadCompletionService` 변경
- 새 ArchUnit 예외 추가, `TransactionTemplate`·self-invocation
- 범위 밖 리팩터링, 손 포맷 정리
- 사람 승인 없이 커밋/푸시

## 참고
- 결정: `docs/decisions/2026-11-05-external-call-outside-transaction.md`, `2026-09-21-mediaupload-orphan-check-no-status-transition.md`
- lessons-log: 경로별 응답 표(2026-11-04, 11-06, 11-07), 같은 에러 코드 분기 `never()`(2026-11-04), H2 `NON_KEYWORDS=KEY`(2026-11-06), 계약 메서드 실제 존재 확인(2026-11-06)
- 쓰는 리포지토리 메서드(모두 기존): `MediaRepository.findByPublicIdForUpdate`, `findByIdForUpdate`, `findById`, `delete`
