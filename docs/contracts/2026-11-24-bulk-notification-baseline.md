# 작업 계약: 대량 알림 발송 로컬 측정 환경과 베이스라인

- **관련 이슈**: #215
- **브랜치**: `Test/#215-bulk-notification-baseline` (처음에는 PR #219 340b964 위에 쌓았고, #219·#227 머지 뒤 2026-11-25 `git rebase --onto origin/develop 340b964`로 `origin/develop` 0300934 위로 옮김)
- **상태**: 완료 (2026-11-25, PR #228)

## 목표
오늘의 질문 공개 알림 배치(#214, `DailyQuestionOpenedNotificationBatchService`)를 받는 사람 1만/10만 규모로 로컬 Docker Postgres(#217 환경)에서 실행해 개선 전 처리 시간과 병목을 기록하고, 100만은 선형 추정한다. FCM 전송 한도를 근거로 개선 목표를 정한다. 운영 코드의 동작은 바꾸지 않는다.

## 결정 사항 (2026-11-24 사람 결정)
| 항목 | 결정 | 기각한 선택지와 이유 |
|---|---|---|
| #217과의 관계 | #219 커밋 위에 쌓는다. `loadtest/` 구조·`00_common.sql`을 그대로 쓰고 `40_*` 파일만 추가 | 머지 대기는 시작이 늦어짐. 별도 디렉터리는 compose·모니터링 중복 |
| 가짜 발송기 등록 | 가짜에 `@Profile("loadtest & !prod") @Primary`. `FcmPushSender` diff 0 | `FcmPushSender`에 `!loadtest`를 붙이면 `ExternalCallConnectionTest`가 타는 Fcm 등록 조건이 바뀜(`docs/decisions/2026-11-24-push-sender-interface.md:57-58`). `@ConditionalOnProperty`는 운영 env 실수로 켜질 수 있음 |
| 발송 시작 | 같은 프로필 전용 `ApplicationRunner`. 설정 `loadtest.daily-question-opened.run-on-startup=true`일 때만 기동 직후 1회 실행 | 컨트롤러는 JWT 서명이나 `SecurityConfig` 전역 화이트리스트 변경이 필요(`SecurityConfig.java:89`). cron env는 회차마다 시각을 맞춰야 함 |
| `handleResults` 길이 가드 | 이번 범위 밖. 별도 이슈로 낸다 | 운영 동작 변경을 측정 PR에 섞지 않음. 가짜 발송기는 크기·순서 규약을 테스트로 고정 |
| 측정 범위 (2026-11-25 변경) | 0ms × {1만, 10만} 실측. 100만은 두 점의 선형성을 확인한 뒤 선형 추정치로 적고 "추정" 표시. 50ms 조건은 측정하지 않고 "0ms 실측 + 회원 수 × 50ms" 계산으로 대체(순차 구조라 지연이 그대로 더해짐) | 처음 계획(0ms 100만 실측 + 50ms 1만·10만)은 약 7시간. 1만 스모크(`results/raw/20261125-151923-*-summary.txt`)의 회원당 13.5ms와 순차 구조로 보아 회원 수에 비례한다고 추정했고, 10만 실측(회원당 12.8ms)으로 뒷받침됨(결과 문서 4절). 100만 실측은 개선 후에 한다 |
| 개선 목표 (2026-11-25) | 두 단계: (1) 서버 처리 능력 — 가짜 발송기(지연 0ms) 기준 100만 건을 1분 안에 발송기로 넘긴다 (2) 실제 발송 — FCM 한도에 맞춰 서버에서 속도를 조절해 100만 명에게 약 2~3분 안에 전달(분당 60만 한도의 약 80% 이하 유지) | "100만 명 1분 안에 실제 발송"은 FCM 기본 한도 분당 60만 건, 셀프 증액 최대 +25%(분당 75만)로 불가능. 근거: https://firebase.google.com/docs/cloud-messaging/throttling-and-quotas , https://firebase.google.com/docs/cloud-messaging/scale-fcm (2026-11-25 확인, 페이지 갱신일은 미확인) |

## 범위: 건드려도 되는 파일
커밋 1 (가짜 발송기·트리거)
- 신규 `src/main/java/kusitms/spin/tikitak/service/notification/push/LoadTestPushSender.java`
- 신규 `src/main/java/kusitms/spin/tikitak/service/notification/LoadTestDailyQuestionOpenedRunner.java`
- 신규 `src/main/java/kusitms/spin/tikitak/global/config/LoadTestProperties.java`, `LoadTestConfig.java` (`@Profile("loadtest & !prod")` + `@EnableConfigurationProperties`. `PropertiesConfig`에는 등록하지 않아 다른 프로필에는 Properties 빈도 없다)
- 신규 테스트: `src/test/java/.../service/notification/push/LoadTestPushSenderTest.java`, `src/test/java/.../global/config/LoadTestConfigTest.java`(또는 같은 목적의 컨텍스트 테스트), Runner 단위 테스트
- 신규 `docs/decisions/2026-11-24-push-sender-loadtest-selection.md`, `docs/decisions/README.md` 행 추가

커밋 2 (시드·측정 스크립트·절차)
- 신규 `loadtest/sql/40_bulk_notification.sql`, 신규 `loadtest/scripts/run-batch-measure.sh`
- 수정 `loadtest/README.md`(#215 절 추가. 기존 #217 절은 고치지 않음), `loadtest/.env.example`(주석·loadtest 변수 추가. 기존 값 변경 없음)

커밋 3 (측정 결과)
- 신규 `loadtest/results/2026-11-xx-bulk-notification-baseline.md` (날짜는 측정일)
- 이 계약 파일

## 상세
- **가짜 발송기** `LoadTestPushSender implements PushSender`
  - `isAvailable()`은 항상 true.
  - `send(tokens, message)`: 토큰이 비면 `List.of()`(Fcm과 같음, `FcmPushSender.java:36-38`). 아니면 호출당 `delay-ms` 한 번 sleep 후, 토큰마다 `(토큰 문자열 + seed)` 해시로 결과를 정한다: `< invalid-token-rate`면 `INVALID_TOKEN`, `< invalid + failure-rate`면 `FAILED`, 아니면 `SUCCESS`. **결과는 입력과 크기·순서가 같다**(`PushSender.java:18` 규약).
  - sleep 중 인터럽트되면 인터럽트 플래그를 복원하고 전부 `FAILED`를 돌려준다(C9: service에서 RuntimeException 생성 금지).
  - Micrometer 카운터 `loadtest.push.results`(태그 `status`)로 토큰별 결과를 센다. 배치 지표 `notification.batch.sends`는 토큰별 FAILED를 success로 세므로(`docs/contracts/2026-11-24-daily-question-opened-notification.md:23`) 실패율은 이 카운터로 본다.
  - 지연은 `send()` 호출(= 회원 한 명) 단위다. FCM `sendEach` 한 번의 왕복 지연을 흉내 낸다.
- **`LoadTestProperties`** prefix `loadtest`: `push.delay-ms`(기본 0, 음수 불가), `push.failure-rate`·`push.invalid-token-rate`(기본 0, 각 0~1, 합 ≤ 1), `push.seed`(기본 0), `daily-question-opened.run-on-startup`(기본 false). 잘못된 값이면 기동 실패. 바인딩은 `ApplicationContextRunner`로 확인(lessons 2026-11-04).
- **Runner**: `run-on-startup=true`면 `sendDailyQuestionOpenedNotifications()`를 한 번 부른다. 트랜잭션 없이 호출한다(C2, 배치 javadoc :26). 스케줄 cron은 건드리지 않으므로, 측정 env에서 `DAILY_QUESTION_OPENED_NOTIFICATION_CRON=-`로 끄도록 절차에 적는다.
- **시드 `40_bulk_notification.sql`**: `TRUNCATE notification, member_device_token` 후, `00_common.sql`이 만든 ACTIVE 회원에게 토큰을 넣는다. 회원당 0개 10% / 1개 75% / 2개 15%, 플랫폼 ANDROID 60% / IOS 40%, `fcm_token = 'loadtest-' || member_id || '-' || n`(unique, 512자 이하). `setseed`로 고정. 매 측정 전에 다시 실행하면 앞 회차가 지운 무효 토큰과 쌓인 알림이 초기화된다. #217의 `20_notification.sql`과 같이 쓰지 않는다(TRUNCATE가 그 알림을 지움)고 README에 적는다.
- **규모**: `00_common.sql`의 `normal_teams`로 정한다. 1만 기준 실측 후 비례로 10만·100만 값을 정하고, 실제 받는 사람 수(배치 시작 로그 `recipientCount`)를 결과에 적는다. 규모를 바꿀 때는 `down -v` 후 재시드(`00_common.sql`은 빈 DB 전제, `00_common.sql:51-58`). **`down -v` 전에는 반드시** `docker compose -f loadtest/docker-compose.yml config | grep '^name'`이 `tikitak-loadtest-push`인지, `docker compose ls -a`에서 CONFIG FILES가 이 폴더(`.../test/loadtest/...`)인 행의 NAME과 같은지 확인한다(`loadtest/README.md` 10-3). 다르면 #217 볼륨이 지워지므로 `.env`부터 고친다.
- **작업 폴더 분리** (`loadtest/README.md` 10절): 이 폴더의 `loadtest/.env`(gitignore)는 `LOADTEST_PROJECT=tikitak-loadtest-push`, Postgres 55433, Redis 56380, Prometheus 59091, Grafana 53001, 앱 `SERVER_PORT=8082`·`MANAGEMENT_SERVER_PORT=8083`, `DB_URL`·`REDIS_PORT`도 새 포트(2026-11-25 설정, `compose config`로 이름·포트 확인). 스크립트는 #217과 같이 `.env`의 포트로 주소를 만든다.
- **측정은 #217과 번갈아 한다** (README 10-2): 측정 전 #217 폴더의 앱을 끄고 `docker compose ... stop`(볼륨 유지), `docker ps`로 `tikitak-loadtest-push-*`만 떠 있는지 확인하고 결과 문서에 적는다. #217 쪽 stop/start는 그 세션과 조율한다.
- **측정 스크립트 `run-batch-measure.sh`**: 토큰·알림 초기화(40번 실행) → `pg_stat_statements` reset → Hikari 샘플러 시작 → 앱을 `dev,loadtest` 프로필·`run-on-startup=true`로 기동 → 배치 종료 로그를 기다림 → `/actuator/prometheus` 스냅샷 저장(`notification_batch_*`, `loadtest_push_results_*`, `hikaricp_*`, `process_cpu_usage`, `jvm_memory_used_bytes`) → 샘플러 종료 → `pgss.sh` 덤프 → 앱 종료. 결과는 `loadtest/results/raw/<RUN_ID>-*`(gitignore됨).
- **결과 문서**: 환경(CPU·메모리·Postgres 버전과 주요 설정·풀 10), 규모별 전체 소요·초당 처리 건수·단계별 소요(받는 사람 조회 / 발송 루프)·앱 CPU·힙·커넥션 사용량·상위 쿼리, 병목 분석, 개선 이슈 후보, FCM 전송 한도(공식 문서 인용)와 위 두 단계 목표. 회원당 대기 시간의 원인(앱↔DB 왕복 지연 가설)은 `SELECT 1` 왕복 시간 측정으로 확인하고, 확인 못 하면 "가설"로 표시. FCM 권고(매시 :00·:15·:30·:45 앞뒤 2분 회피, 최대 속도까지 60초 이상 램프업, 429 시 retry-after)와 자정 발송의 충돌은 개선 후보로 적는다.

## 의도된 동작 변경
없음. `loadtest` 프로필이 없거나 `prod` 프로필이 함께 켜지면 새 빈(가짜 발송기, Runner, Properties, Config)이 하나도 등록되지 않는다.
- `loadtest` 프로필에서는 배치뿐 아니라 이벤트 리스너 알림(피드·태그·댓글 등, 모두 `NotificationService.send` 경유)도 가짜 발송기로 간다. 측정 전용이므로 의도된 것.

## 완료 조건
- [x] `./gradlew test`, `./gradlew spotlessCheck` 통과. ArchUnit 포함
- [x] 가짜 발송기 테스트: 입력 토큰 N개에 결과 N개, 같은 입력이면 같은 순서·같은 결과(결정적), 빈 입력은 빈 리스트, rate 0이면 전부 SUCCESS, invalid 1이면 전부 INVALID_TOKEN, 비율이 대략 맞음(예: 10,000개에 ±2%p), 인터럽트 시 전부 FAILED·플래그 복원
- [x] 컨텍스트 테스트: 프로필 없음 → `LoadTestPushSender`·Runner·`LoadTestProperties` 빈 0개 / `loadtest` → 등록되고 `PushSender` 단일 주입이 가짜로 결정됨 / `loadtest,prod` → 0개 / 잘못된 rate(합 > 1, 음수 지연) → 기동 실패
- [x] Runner 테스트: `run-on-startup=false`면 배치 미호출(`never()`), true면 1회 호출
- [x] `FcmPushSender.java`, `NotificationService.java`, `SecurityConfig.java`, `application*.yaml` diff 0 (`git diff --stat origin/develop...HEAD`로 확인)
- [x] `40_bulk_notification.sql`을 1만 규모에 실행해 토큰 분포·unique 위반 없음을 확인 쿼리로 확인하고 README에 실행 기록
- [x] `down -v`는 `compose config`의 `name`이 `tikitak-loadtest-push`임을 확인한 뒤에만 실행. 측정 중 다른 폴더 환경이 멈춰 있었음을 결과 문서에 기록
- [x] 측정: 0ms × {1만, 10만} 각 1회 이상(#217 환경 정지 상태), 결과 문서에 원시 숫자·RUN_ID·실행 명령. 100만과 50ms 조건은 계산식과 함께 "추정"으로 표시
- [x] 결과 문서에 두 단계 개선 목표와 그 근거(FCM 한도 수치)를 적는다
- [x] FCM 전송 한도는 공식 문서 URL과 인용으로 적고, 확인 못 한 것은 "미확인"

## 커밋 구성
1. `[Test] 측정 전용 가짜 푸시 발송기와 배치 실행 Runner 추가 (#215)` — 커밋 1 범위
2. `[Test] 대량 알림 측정용 디바이스 토큰 시드와 측정 스크립트 추가 (#215)` — 커밋 2 범위
3. `[Test] 대량 알림 발송 베이스라인 측정 결과 추가 (#215)` — 결과 문서, 계약 갱신
4. PR 생성 후 계약 상태 "완료" 커밋

## 하지 말 것
- 배치·`NotificationService`·`FcmPushSender`·`SecurityConfig` 등 운영 코드 수정, 최적화 선반영(이번은 개선 전 기준선)
- `application*.yaml`에 loadtest 프로필 파일 추가(설정은 `loadtest/.env`로만 넣는다)
- dev Supabase 사용, 실제 FCM 호출
- #217이 만든 기존 파일(`00/10/20/30` SQL, k6, 기존 스크립트)의 동작 변경. README는 #215 절 추가만
- `Hikari maximum-pool-size` 변경(10 고정)
- 사람 승인 없이 커밋/푸시

## 남은 리스크
- 운영의 `SPRING_PROFILES_ACTIVE` 실제 값은 GitOps 저장소에 있어 **미확인**. `!prod` 조건은 운영이 `prod` 프로필을 쓴다는 전제(`application-prod.yaml` 존재로 추정)
- 매 회차가 새 JVM 기동이라 JIT 웜업 전 구간이 포함된다. 결과에 명시
- `00_common.sql`의 팀 이름 `lpad(team_id, 5)`는 team_id ≥ 100000에서 잘려 이름이 겹친다(unique 없음, 측정 영향 없음으로 추정, 미확인)
- 100만 규모 시드 소요 시간·디스크 용량 미확인
- 1만·10만 베이스라인은 `measure-lock.sh`·외부 부하 샘플러(#227) 도입 전에 측정했다. #217 컨테이너를 멈춘 상태였고 `docker ps` 기록은 남았지만, 호스트 외부 부하 기록은 없다(결과 문서에 표시)

## 참고
- `docs/decisions/2026-11-24-push-sender-interface.md:59, :70-73`, `docs/contracts/2026-11-24-daily-question-opened-notification.md:23, :87-93`
- 메트릭: `DailyQuestionOpenedNotificationBatchService.java:33-37`, 받는 사람 조회 `TeamMemberRepository.java:207-221`
- #217 환경: `loadtest/README.md`, `loadtest/scripts/{hikari-sampler,pgss,run-measure}.sh`
- lessons-log 2026-11-04(기동 검증은 컨텍스트로), 2026-11-04(같은 결과 분기는 `never()`로 고정)
