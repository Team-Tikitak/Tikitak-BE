# 결정 기록 (Decision Log)

작업하면서 내린 판단/결정과 그 이유를 남겨두는 곳. 
결정 하나당 파일 하나(`YYYY-MM-DD-슬러그.md`)로 관리하고, 여기서는 목록만 훑어본다.

| 날짜 | 결정 | 관련 이슈 | 한 줄 요약 |
|---|---|---|---|
| 2026-09-19 | [피드 목록 N+1을 EntityGraph 확장 대신 배치 조회로 해결](2026-09-19-feed-n1-batch-fetch-pattern.md) | #153 | 페이지네이션 쿼리에 컬렉션을 EntityGraph로 fetch join하면 row multiplication이 생기므로, 기존 commentCounts류와 같은 `feedId IN (...)` 배치 조회 패턴을 재사용 |
| 2026-09-19 | [이미지 배치 조회는 count/첫 이미지 쿼리 분리 대신 전체 리스트 단일 쿼리](2026-09-19-feed-image-single-list-query.md) | #153 | `MAX_IMAGE_COUNT=10`으로 상한이 있어 전체를 한 번에 가져와 Java에서 개수/정렬/첫 항목을 뽑는 게 쿼리 2개보다 단순 |
| 2026-09-19 | [Map<Long, List<>> + groupingBy를 쓴 이유](2026-09-19-groupingby-for-one-to-many-batch-maps.md) | #153 | 이미지/태그는 피드 하나에 여러 개 붙는 1:N 관계라 toMap이 아니라 groupingBy로 묶어야 함 |
| 2026-09-21 | [MediaUpload 정리는 즉시 삭제 대신 기존 만료 배치에 편입](2026-09-21-mediaupload-cleanup-fold-into-batch.md) | #154 | Media가 나중에 USED가 될 수 있어 완료 시점 즉시 삭제는 위험, 기존 배치에 4번째 단계로 편입 |
| 2026-09-21 | [MediaUpload 삭제 조건은 status 전이 없이 자식 없음+expiresAt만으로 판단](2026-09-21-mediaupload-orphan-check-no-status-transition.md) | #154 | Media.upload는 생성 시 한 번만 세팅되는 단조 관계라 not-exists 체크만으로 안전하게 삭제 가능 |
| 2026-09-21 | [media_upload.expires_at 인덱스는 필터가 아니라 ORDER BY+LIMIT 최적화용](2026-09-21-media-upload-expires-at-index-tradeoff.md) | #154 | 필터 선택도는 낮지만 정리 배치의 ORDER BY+LIMIT 쿼리가 테이블 커질수록 비싸져서 예방적으로 추가 |
| 2026-09-21 | [CREATE INDEX CONCURRENTLY + Flyway 기본 락 설정의 자기 자신 데드락](2026-09-21-flyway-concurrent-index-deadlock.md) | #154 | Flyway가 트랜잭션 안에서 들고 있는 schema-history 락 때문에 CONCURRENTLY가 무한 대기, transactionalLock=false로 해결 |
| 2026-11-02 | [ArchUnit 기존 위반은 동결 대신 명시적 예외 목록으로 허용](2026-11-02-archunit-explicit-exceptions.md) | - | FreezingArchRule store는 무엇을 왜 허용했는지 리뷰에서 보이지 않아, 규칙별 예외 상수에 이유·후속 조치 주석을 달아 관리 |
| 2026-11-04 | [Spotless는 레이아웃 포맷터 없이 공백·import 최소 규칙만 적용](2026-11-04-spotless-minimal-rules.md) | - | 레이아웃 포맷터는 공백 무시 후에도 2,500~5,700줄이 바뀌어 동작 변경 0을 확인할 수 없으므로, 탭·줄 끝 공백·파일 끝 개행·import만 고정. `ratchetFrom`은 기능 PR을 오염시켜 기각 |
| 2026-11-04 | [JVM 타임존은 바꾸지 않고 날짜·월 경계만 KST로 계산](2026-11-04-kst-boundaries-without-tz-change.md) | #162 | JVM TZ 고정은 기존 UTC 저장값과 9시간 불연속·blue/green 마이그레이션 위험이 있어, 저장은 그대로 두고 경계만 KST로 계산해 시스템 zone으로 변환. `@Scheduled`는 zone 명시 |
| 2026-11-05 | [외부 API 호출(OAuth/FCM/R2)은 DB 트랜잭션·락 밖에서 실행](2026-11-05-external-call-outside-transaction.md) | #161 | 트랜잭션 구간을 패키지별 별도 빈으로 빼고(self-invocation·TransactionTemplate 대신), 업로드 완료는 사전 읽기 → headObject → 락 후 전체 재검증 |
| 2026-11-05 | [감사 필드는 domain/common의 2단계 상위 클래스와 Spring Data auditing으로 모음](2026-11-05-base-time-entity.md) | #159, #183 | `BaseCreatedTimeEntity`/`BaseTimeEntity` + `@SuperBuilder`, `deletedAt`·커스텀 `DateTimeProvider` 제외. 2단계(#183)에서 나머지 6개 적용: flush 전 응답·컬렉션만 수정을 위해 Feed/FeedComment는 `touch()` 사용, Member 계열은 auditing만 |
| 2026-11-05 | [refresh token 재사용은 유예 없이 탐지하고, revoke된 행은 만료 전까지 남김](2026-11-05-refresh-token-reuse-strict.md) | #163 | revoke된 토큰 재전송·동시 재발급 패배(조건부 update 0행)를 유예 없이 재사용으로 보고 회원의 refresh token 전부 revoke, 응답은 AUTH007 유지(`noRollbackFor`). 정리 배치는 `expires_at`만 기준. jti 추가, secret 32바이트 기동 검증 |
| 2026-11-09 | [회원 소셜 계정은 ACTIVE 부분 unique 인덱스로 막고, 동시 첫 로그인에서 진 요청은 1회 재시도](2026-11-09-member-social-unique-retry.md) | #181 | `(social_provider, provider_id) WHERE status='ACTIVE'` 일반 unique 인덱스(#71 이전 탈퇴 행 허용, CONCURRENTLY 미사용). 같은 트랜잭션 catch는 PG abort·rollback-only로 불가해 `AuthService`에서 회원 트랜잭션 호출만 1회 재시도, 진 쪽 `isNewMember=false`. ON CONFLICT·REQUIRES_NEW·advisory lock 기각 |
| 2026-11-12 | [미디어 삭제·정리 배치의 R2 삭제는 DB 전이 커밋 뒤, 락 밖에서 한다](2026-11-12-media-delete-r2-after-commit.md) | #180 | 락·재검증·DELETED 전이는 `MediaDeletionService`, R2 삭제는 커밋 뒤 트랜잭션 밖(head 제거, 404 성공). 실패는 기존 DELETED 하드 삭제 배치가 다시 지움. 하드 삭제만 R2 먼저 → 락 후 재확인·행 삭제. 아웃박스·지연 삭제만 기각 |
| 2026-11-13 | [팀 프로필 media 검증 실패는 세 경로 모두 400 MEDIA018](2026-11-13-team-profile-media-fail-policy.md) | #194 | 팀 생성·프로필 수정의 null 무시(`4312be0`, 이유 기록 없음)를 원래 설계대로 MEDIA018로 되돌리고 `TeamProfileMediaResolver`로 통일. 프로필 수정만 현재 연결된 media 재전송을 "변경 없음"으로 통과. null 무시 유지·미존재만 MEDIA009·재전송도 MEDIA018 기각 |
