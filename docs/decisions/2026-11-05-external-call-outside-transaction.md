# 외부 API 호출(OAuth/FCM/R2)은 DB 트랜잭션·락 밖에서 실행

- **날짜**: 2026-11-05
- **관련 이슈**: #161
- **상태**: 결정됨, 적용 완료
- **계약**: [2026-11-05-external-call-tx-separation](../contracts/2026-11-05-external-call-tx-separation.md)

## 상황 (Context)
OAuth 로그인(카카오/구글/애플 API), 알림 전송(FCM `sendEach`), 업로드 완료(R2 `headObject`)가 `@Transactional` 안에서 외부 네트워크를 호출했다. 외부 응답을 기다리는 동안 DB 커넥션을 잡고 있어서(업로드 완료는 PESSIMISTIC_WRITE 락까지), 외부 서비스가 느려지면 커넥션 풀(10)이 바닥나 관계없는 API까지 막힐 수 있었다.

## 검토한 선택지 (Options considered)
1. 같은 클래스 안에서 `@Transactional` 메서드를 나눠 `this.`로 호출
2. `TransactionTemplate`으로 서비스 메서드 안에서 트랜잭션 구간을 직접 지정
3. 트랜잭션 구간을 패키지마다 **별도 빈**(`OAuthMemberLoginService`, `NotificationPersistService`, `MediaUploadCompletionService`)으로 빼고, 원래 서비스는 트랜잭션 없이 "DB → 외부 호출 → DB" 순서만 조율

## 결정 (Decision)
3번. 원칙: **외부 네트워크 호출은 트랜잭션·락 밖에서 한다.** 트랜잭션 안에서는 DB 작업만 하고, 외부 호출에 필요한 값(ID, 토큰, key 등)만 꺼내 돌려준다(엔티티 반환 금지, 트랜잭션 밖 LAZY 접근 방지).

## 이유 (Rationale)
- 1번은 Spring 프록시를 거치지 않아 `@Transactional`이 조용히 무시된다(self-invocation).
- 2번은 동작은 하지만 트랜잭션 경계가 애너테이션 규칙(ArchUnit C2: `@Transactional`은 service 패키지에서만)과 따로 놀아 리뷰에서 경계를 찾기 어렵다. 기존 `ObjectDeleteOutboxProcessor`도 별도 빈 방식이다.
- 3번은 트랜잭션 경계가 빈 메서드의 애너테이션으로 드러나고, 단위 테스트에서 빈 호출 여부로 경계를 검증할 수 있다.

### 업로드 완료: 사전 읽기 → 외부 호출 → 락 후 전체 재검증
- `prepare`(readOnly, 락 없음)에서 기존 검증을 하고 `headObject` 대상만 반환 → `headObject` × N(트랜잭션 없음) → `complete`(PESSIMISTIC_WRITE)에서 **모든 검증을 다시** 하고 상태를 바꾼다.
- 재검증하는 이유: 락을 놓은 사이 `deleteUnusedMedia`·정리 배치가 media를 DELETED로 바꾸거나, 중복 완료 요청이 먼저 upload를 COMPLETED로 바꿀 수 있다. 락 단계가 최종 판정을 하므로 이 경우 지금과 같은 MEDIA014로 끝나고 상태를 바꾸지 않는다.
- 사전 단계에서 만료를 발견하면 예외 대신 "만료" 신호를 돌려 `headObject`를 건너뛰고, 락 단계에서 `expire()`를 저장한 뒤 MEDIA014를 던진다(`noRollbackFor`). upload 만료 전이는 이 경로에서만 일어나므로 저장을 잃으면 안 된다([관련 결정](2026-09-21-mediaupload-orphan-check-no-status-transition.md)).

## 의도된 동작 변경
계약의 ["의도된 동작 변경" 표](../contracts/2026-11-05-external-call-tx-separation.md#의도된-동작-변경)를 따른다. 요약: OAuth 트랜잭션 시작·커밋 실패가 AUTH105로 바뀜, 앱 로그인 Redis 저장 실패 시 회원 생성은 커밋된 채 남음, FCM 실패·무효 토큰 삭제 실패와 무관하게 알림 레코드가 남음, 업로드 완료에서 여러 파일이 서로 다른 오류를 가지면 상태 오류(MEDIA014 등)가 R2 오류보다 우선.

## 트레이드오프 / 남은 리스크
- 업로드 완료는 사전 단계 트랜잭션 하나(upload·media 조회 2회)가 늘고, 중복 완료 요청이 겹쳐서 실행되면 둘 다 `headObject`를 부른 뒤 늦은 쪽이 락 단계에서 MEDIA014로 끝난다(앞 요청 커밋 뒤에 도착한 요청은 사전 단계에서 MEDIA014, R2 호출 없음).
- 커넥션은 잡지 않지만 요청 자체는 외부 응답만큼 오래 걸린다. Apple JWKS·FCM(`FirebaseOptions`)·R2 `S3Client`에는 아직 타임아웃이 없다(후속 이슈).
- `deleteUnusedMedia`와 정리 배치(`deleteExpired*Media`)는 여전히 락 안에서 R2를 호출한다(후속 이슈). → #180에서 해소: [DB 전이 커밋 뒤 락 밖에서 R2 삭제](2026-11-12-media-delete-r2-after-commit.md).
- 같은 소셜 계정의 동시 첫 로그인에서 회원이 중복 생성될 수 있는 위험은 분리 전후 모두 있다(조회 후 생성, 락·unique 제약 없음). 회원 unique 제약은 후속 이슈.
