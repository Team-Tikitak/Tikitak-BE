# CREATE INDEX CONCURRENTLY + Flyway 기본 락 설정은 자기 자신과 데드락에 빠진다

- **날짜**: 2026-09-21
- **관련 이슈**: #154 (MediaUpload 레코드 정리 로직 부재)
- **상태**: 결정됨, 적용 완료

## 상황 (Context)
코드 리뷰에서 "`V24`(media_upload.expires_at 인덱스)를 운영에 배포할 때 일반 `CREATE INDEX`는 인덱스 생성 중 쓰기(INSERT)를 대기시키니 `CREATE INDEX CONCURRENTLY` + Flyway `executeInTransaction=false`를 쓰라"는 지적을 받았다. 반영한 뒤 Testcontainers로 재검증하다가 `MediaUploadRepositoryIntegrationTest`가 이전엔 37초면 끝나던 게 8분 넘게 끝나지 않는 걸 발견했다.

## 검토한 선택지 (Options considered)
- (조사 단계) `pg_stat_activity`를 직접 조회해 원인 파악: Flyway 자신의 커넥션이 `SELECT COUNT(*) FROM pg_namespace ...` 실행 후 `idle in transaction` 상태로 멈춰있었고, 우리 `CREATE INDEX CONCURRENTLY` 문은 `virtualxid` 락을 기다리며 블록돼 있었다. `CREATE INDEX CONCURRENTLY`는 "이 테이블을 볼 수 있는 모든 트랜잭션이 끝나야" 진행되는데, 그 트랜잭션이 하필 Flyway 자신이 schema-history 락을 잡으려고 열어둔 트랜잭션이라 **자기 자신을 기다리는 데드락**이었다.
1. `V24`에서 `CREATE INDEX CONCURRENTLY` 자체를 포기하고 일반 `CREATE INDEX`로 되돌린다.
2. Flyway의 `postgresql.transactionalLock` 설정을 `false`로 바꿔, Flyway가 자신의 schema-history 락을 트랜잭션이 아니라 세션 단위 advisory lock(`pg_advisory_lock`/`unlock`)으로 잡도록 한다.

## 결정 (Decision)
2번(`spring.flyway.postgresql.transactional-lock: false`)을 선택했다. `application.yaml`(운영/개발 공통)과 `application-integration-test.yaml`(테스트) 양쪽에 반영.

## 이유 (Rationale)
- 1번은 애초에 리뷰가 지적한 "운영 쓰기 차단" 문제를 다시 되살리는 선택이라 기각.
- Flyway 11.x(`flyway-database-postgresql`)에 `PostgreSQLConfigurationExtension.transactionalLock`이 실제로 존재함을 jar를 까서 확인했고, 이게 정확히 "non-transactional 마이그레이션과 Flyway 자체 락이 충돌하는" 이 케이스를 위한 공식 옵션이었다.
- 이 설정은 전역(마이그레이션 러너 전체)에 적용되는 것이라 `V24`만이 아니라 앞으로 `executeInTransaction=false`를 쓰는 어떤 마이그레이션에도 동일하게 적용된다.

## 트레이드오프 / 남은 리스크
- 이걸 놓쳤다면 이 마이그레이션이 **prod 배포 시 앱 시작 자체를 무한 대기**시켰을 것이다(Flyway가 기본적으로 앱 부팅 시점에 동기로 돈다) — "쓰기 몇 건 지연"보다 훨씬 심각한 장애였을 뻔했다. 배포 전에 Testcontainers 통합 테스트로 미리 재현/검증해 둔 덕분에 잡을 수 있었다.
- `transactionalLock: false`는 Flyway 자신의 동시 실행 방지 락 메커니즘을 바꾸는 것이므로, 여러 인스턴스가 동시에 마이그레이션을 시도하는 배포 방식(예: 다중 replica가 동시에 앱을 기동)이라면 락 동작 차이를 한 번 더 검토할 가치가 있다(현재는 단일 마이그레이션 러너 전제로 판단).
