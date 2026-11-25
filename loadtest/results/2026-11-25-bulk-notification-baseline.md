# 대량 알림 발송 베이스라인 (#215) — 2026-11-25

오늘의 질문 공개 알림 배치(`DailyQuestionOpenedNotificationBatchService`, #214)를 받는 사람 1만·10만 규모로 로컬 Docker Postgres에서 실행한 개선 전 기준선이다. 실제 FCM 대신 가짜 발송기(`LoadTestPushSender`, 지연 0ms)를 썼다. 100만은 실측하지 않고 두 점에서 선형 추정했다.
계약: `docs/contracts/2026-11-24-bulk-notification-baseline.md`. 절차: `loadtest/README.md` 11절.

원시 파일은 `loadtest/results/raw/`에 있다(커밋하지 않는다). 집계값마다 원시 파일, 구간, 표본 수를 적었다. 원시 파일에서 다시 계산한 값은 "계산", 근거 있는 짐작은 "추정"(계산식 포함), 확인하지 않은 원인 설명은 "가설", 원시 파일 없이 콘솔에서 본 값은 "콘솔 확인, 원시 파일 없음"으로 표시했다.

## 요약

| | 1만 | 10만 | 100만 (**추정**) |
|---|---|---|---|
| 받는 사람 | 10,024명 | 100,243명 | 1,000,000명 |
| 전체 소요 | 138.1초 (2분 18초) | 1,287.0초 (21분 27초) | 약 12,839초 (약 3시간 34분) |
| 초당 처리 | 72.6명 | 77.9명 | (10만과 같다고 가정) |
| 회원당 시간 | 13.78ms | 12.84ms | 12.84ms (10만 값) |

- 출처: 1만 `20261125-154322-batch-baseline-10k-0ms-summary.txt`, 10만 `20261125-154900-batch-baseline-100k-0ms-summary.txt`의 배치 종료 로그 `recipientCount`·`elapsedMs`. 초당 처리는 `recipientCount ÷ elapsedMs`, 회원당 시간은 `elapsedMs ÷ recipientCount`로 계산.
- 100만 추정 = 1,000,000 × 12.839ms(10만 실측 회원당 시간). 1만 값(13.78ms)으로 계산하면 13,780초(약 3시간 50분)다. 두 점의 회원당 시간 차이는 6.8%이고 회원 수가 늘어도 회원당 시간이 커지지 않았다(4절).
- **병목**: 회원 한 명마다 트랜잭션 1개(DB 왕복 약 4회, 추정)를 차례로 쓰는 단일 스레드 구조다. 10만 회원당 12.84ms 중 Postgres 실행 시간은 0.36ms(2.8%)뿐이고, 나머지는 앱↔DB 왕복(약 5.6ms, 추정)과 앱 처리(약 6.3ms, 추정)다. 커넥션 10개 중 1개, 호스트 논리 CPU 8개 중 평균 0.41개만 썼다(5절).
- **목표**(계약 결정 사항): (1) 서버 처리 능력 — 가짜 발송기 0ms 기준 100만 명을 1분 안에 발송기로 넘긴다 = 초당 약 16,667명, 지금(초당 77.9명)의 약 214배. (2) 실제 발송 — FCM 한도(분당 60만)의 약 80%(분당 48만) 이하로 속도를 조절해 100만 명에게 약 2~3분 안에 전달한다(7절).

## 1. 환경과 조건

| 항목 | 값 | 근거 |
|---|---|---|
| 호스트 CPU | Intel Core i7-8565U @ 1.80GHz, 4코어 8스레드. `nproc` 8, 앱 `system_cpu_count` 8.0 | `Win32_Processor`(2026-11-25 작성 시 조회), `-prometheus.txt` |
| 호스트 메모리 | 16,199MB (`TotalVisibleMemorySize` 16,587,552KB) | `Win32_OperatingSystem`(작성 시 조회). 측정 당시 여유 메모리는 기록하지 않았다(미확인) |
| Docker | 29.9.2 (`docker version`, 작성 시 조회). Docker Desktop 할당 CPU·메모리는 이번에 다시 확인하지 않았다(2026-11-24 #217 문서 기준 8 CPU, 7.67GiB) | |
| Postgres | `postgres:17.6-alpine`, CPU 2개(`cpus: 2`), 메모리 2GB(`mem_limit: 2g`), `/dev/shm` 256MB | `loadtest/docker-compose.yml` |
| Postgres 설정 | `shared_buffers` 512MB, `effective_cache_size` 1536MB, `work_mem` 4MB, `maintenance_work_mem` 256MB, `max_connections` 100, `max_wal_size` 4GB, `track_io_timing` on, `pg_stat_statements.track=all`, `timezone` Asia/Seoul | `docker-compose.yml` command. 실제 적용값(`pg_settings`)과 `version()` 문자열은 이번 회차 원시 파일에 없다(미확인). DB 조회는 하지 않았다 |
| 그 밖의 컨테이너 | Redis 1 CPU/256MB, Prometheus 0.5 CPU/512MB(수집 1초), Grafana 0.5 CPU/512MB | `docker-compose.yml`, README "고정 환경값" |
| 작업 폴더 분리 | 프로젝트 `tikitak-loadtest-push`, Postgres 55433, 앱 8082·actuator 8083 | `-summary.txt`의 `project`, `-app.log`(`Tomcat started on port 8082`, `8083`) |
| JVM | Java HotSpot 22.0.2+9-70(Oracle), G1, 힙 옵션 없음. Old Gen 최대 4,246,732,800 bytes(약 4,050MiB) | `-app.log`(`using Java 22.0.2`), `-prometheus.txt`(`jvm_info`, `jvm_memory_max_bytes`) |
| 앱 | `build/libs/tikitak-0.0.1-SNAPSHOT.jar`(빌드 15:10), `-Duser.timezone=Asia/Seoul`, 프로필 `dev,loadtest` | jar 수정 시각(`build/libs/tikitak-0.0.1-SNAPSHOT.jar` 15:10:58), `-summary.txt`의 `jar`, `-app.log`(`The following 2 profiles are active: "dev", "loadtest"`) |
| 측정 당시 코드 | `git_head=6afc0be`, src 미커밋 변경 없음. 6afc0be는 rebase 전 커밋이고, 현재 브랜치 HEAD와 `src/` 차이는 없다(`git diff --stat 6afc0be HEAD -- src` 출력 없음) | `-summary.txt` |
| Hikari 풀 | 최대 10 (`hikari_maximum_pool_size=10`, `hikaricp_connections_max` 10.0) | `-summary.txt`, `-prometheus-batch.txt` |
| 가짜 발송기 | 지연 0ms, 실패율 0, 무효 토큰 비율 0, seed 0. 로그 `Load test push sender enabled. delayMs=0, failureRate=0.0, invalidTokenRate=0.0, seed=0` | `-summary.txt` [조건], `-app.log` |
| 스케줄 | `DAILY_QUESTION_OPENED_NOTIFICATION_CRON=-`로 이 배치의 cron은 끔. 배치는 기동 직후 `LoadTestDailyQuestionOpenedRunner`가 한 번 실행 | `run-batch-measure.sh` |

### 측정 방법

`loadtest/scripts/run-batch-measure.sh`가 한 회차를 묶어 실행한다(README 11-3): 환경 확인 → `40_bulk_notification.sql`(토큰·알림 초기화와 재시드) → `pg_stat_statements` reset → Hikari 샘플러(1초)·JVM 샘플러(5초) 시작 → 앱을 `dev,loadtest`로 새로 기동 → 배치 종료 로그 대기 → `/actuator/prometheus` 스냅샷 → 샘플러 종료 → `pg_stat_statements` 덤프 → 앱 종료.

| 회차 | 실행 명령 | RUN_ID |
|---|---|---|
| 1만 | `bash loadtest/scripts/run-batch-measure.sh --delay-ms 0 --label baseline-10k-0ms` | `20261125-154322` |
| 10만 | `bash loadtest/scripts/run-batch-measure.sh --delay-ms 0 --label baseline-100k-0ms` | `20261125-154900` |

실행 명령은 `-summary.txt`의 `label`·`push_delay_ms`와 맞춰 적었다. 명령 줄 자체는 원시 파일에 남지 않는다(콘솔 확인, 원시 파일 없음).

**매 회차 새 JVM이다.** 앱 기동 직후 배치가 돌므로 JIT 웜업 전 구간이 배치 시간에 들어간다. 웜업을 따로 하지 않았다.

### 구간 (KST)

| 회차 | 시드(RUN_ID) | 앱 기동 | `Started TikitakApplication` | 배치 시작(계산) | 배치 종료 |
|---|---|---|---|---|---|
| 1만 | 15:43:22 | 15:43:25 | 15:43:56.991 (30.0초) | 15:43:57.210 | 15:46:15.337 |
| 10만 | 15:49:00 | 15:49:08 | 15:49:41.695 (31.565초) | 15:49:41.968 | 16:11:08.995 |

배치 시작 = 종료 로그 시각 − `elapsedMs`(계산). Runner 시작 로그(1만 15:43:57.207, 10만 15:49:41.966)와 맞는다. 출처는 각 회차 `-summary.txt`(`app_launched_at`, `completed_log`)와 `-app.log`.

### 다른 부하

- **#217 폴더 환경은 멈춰 있었다.** 두 회차의 `-docker-ps.txt`에는 `tikitak-loadtest-push-{grafana,postgres,redis,prometheus}` 4개만 있고, `-summary.txt`의 `other_containers`는 비어 있다. (스모크 두 회차 `20261125-151427`, `20261125-151923`은 #217 컨테이너 4개가 떠 있던 상태라 결과로 쓰지 않는다.)
- **호스트 외부 부하 기록은 없다.** 두 회차는 `measure-lock.sh`와 외부 부하 샘플러(#227, README 10-4·10-5)를 도입하기 전에 측정했다. 측정 중 gradle·IDE·백신 등의 CPU 사용은 알 수 없다.
- #217 쪽 세션이 15:41:36~15:41:51에 CPU 부하(외부 부하 샘플러 시험)를 걸었다는 전달을 받았다. 이 시각은 #217 세션이 알려 준 값이고 이 폴더에 원시 파일은 없다. 1만 회차 시드 시작(15:43:22)보다 1분 31초 앞서 끝났으므로 두 정식 측정 구간(15:43:22~15:46:15, 15:49:00~16:11:09)과 겹치지 않는다(시각 비교).
- 다만 README 10-5에는 #217 쪽 샘플러 시험 중 하나(15초)가 "#215 측정 시간대"에 돌았다고 적혀 있다(호스트 샘플 1개에 `java` 2.58 포함). 그 시각이 정식 두 회차 안인지는 기록이 없어 **미확인**이다(9절).
- **같은 앱의 다른 `@Scheduled` 배치**: 새벽 배치(04:00, 04:15, 04:30, 04:45)는 구간 밖이다. 30분 주기 `ObjectDeleteOutboxRetryBatchService`(`0 */30 * * * *`)는 10만 구간 안의 16:00:00에 한 번 돌았다. 10만 `-pgss.txt`에 `select id from object_delete_outbox ...`가 calls 1, 0행, 0.2ms로 남았고, 1만 `-pgss.txt`에는 없다. `-app.log`에는 두 회차 모두 이 배치의 로그 줄이 없다(대상 0건). DB 기준으로는 영향이 무시할 수준이다(0.2ms). 다만 같은 16:00 무렵 `-jvm.csv` 샘플의 JVM CPU가 0.377로 10만 구간에서 두 번째로 높다(구간 평균 0.0517). 이 배치 때문인지는 확인하지 않았다(미확인).
- `-app.log`의 `AsyncRequestNotUsableException` ERROR(1만 15:44:01에 3건, 10만 15:49:46에 1건)는 배치 시작 직후 샘플러의 `curl --max-time 2`가 actuator 응답을 기다리다 끊은 것이다(`ClientAbortException`). 배치와는 관계없다(README 11-4). 그래서 배치 시작 후 1만은 약 5~6초, 10만은 약 2~3초 동안 Hikari·JVM 샘플이 비어 있다(3절).

## 2. 시드

| 항목 | 1만 | 10만 | 출처 |
|---|---|---|---|
| `00_common.sql` `normal_teams` | 2,340 | 23,340 | 1만: README 11-5. 10만: 콘솔 확인, 원시 파일 없음 |
| `00_common.sql` 소요 | 약 3초 (`time` real 2.968s, README 11-5) | real 12.9초 | 10만: 콘솔 확인, 원시 파일 없음 |
| 10만 준비 때 `40_bulk_notification.sql` 단독 실행 | | real 6.4초 | 콘솔 확인, 원시 파일 없음 |
| 측정 회차 안의 40번 실행 (`seed_seconds`) | 2초 | 7초 | `-summary.txt` |
| 회원(모두 ACTIVE) | 10,024 | 100,243 | `-seed.log` "건수" |
| 디바이스 토큰 | 10,548 | 105,323 | 〃 |
| 토큰 0개 / 1개 / 2개 회원 | 1,006 (0.100) / 7,488 (0.747) / 1,530 (0.153) | 10,061 (0.100) / 75,041 (0.749) / 15,141 (0.151) | `-seed.log` 분포 |
| ANDROID / IOS | 6,423 (0.609) / 4,125 (0.391) | 63,216 (0.600) / 42,107 (0.400) | `-seed.log` 플랫폼 |
| 무결성(`fcm_token` 중복, 512자 초과, ACTIVE 아닌 회원 토큰) | 0 / 0 / 0, 최대 길이 16 | 0 / 0 / 0, 최대 길이 17 | `-seed.log` 무결성 |
| 받는 사람 예상(`recipients` / 토큰 있는 사람 / 그 토큰) | 10,024 / 9,018 / 10,548 | 100,243 / 90,182 / 105,323 | `-seed.log` 마지막 표 |

10만 준비는 `down -v` → `up -d --wait` → Flyway → 00 → 40 순서로 했다(README 11-2). `down -v` 직전에 `docker compose ls -a`에서 이 폴더(`.../test/loadtest/docker-compose.yml`) 행의 NAME이 `tikitak-loadtest-push`이고 `config | grep '^name'`도 `name: tikitak-loadtest-push`임을 확인했고, 뒤에 `docker volume ls`에 #217의 `tikitak-loadtest-{grafana,pgdata,prometheus}`가 남아 있음을 확인했다(콘솔 확인, 원시 파일 없음). 10만 시드 직후 콘솔에서 본 받는 사람 수 100,243은 측정 회차 `-seed.log`의 값과 같다. `down -v`로 1만 회차의 Prometheus 데이터는 지워졌다(원시 파일은 남아 있다).

## 3. 결과

| 항목 | 1만 | 10만 | 출처 / 계산 |
|---|---|---|---|
| 받는 사람(`recipientCount`) | 10,024 | 100,243 | `-summary.txt` 배치 로그 |
| `successCount` / `failureCount` | 10,024 / 0 | 100,243 / 0 | 〃 |
| 가짜 발송기가 받은 토큰(`loadtest_push_results_total`) | SUCCESS 10,548, FAILED 0, INVALID_TOKEN 0 | SUCCESS 105,323, FAILED 0, INVALID_TOKEN 0 | `-prometheus-batch.txt`. 합계가 시드의 `recipient_tokens`(10,548 / 105,323)와 같다 |
| 전체 소요(`elapsedMs`) | 138,127ms | 1,287,027ms | 배치 종료 로그. Prometheus `notification_batch_duration_seconds_sum` 138.128 / 1,287.03초와 같다 |
| 받는 사람 조회(`recipient_query_duration_seconds_sum`) | 0.899초 | 0.919초 | `-summary.txt` Prometheus 스냅샷. 같은 쿼리의 Postgres 실행 시간은 46.7ms / 365.5ms(`-pgss.txt`, calls 1) |
| 발송 루프(`send_duration_seconds_sum`) | 137.216초 | 1,286.10초 | 〃. 전체의 99.3% / 99.9%(계산) |
| 초당 처리 | 72.6명 | 77.9명 | 계산: `recipientCount ÷ elapsedMs` (`-summary.txt`의 `recipients_per_sec`와 같다) |
| 회원당 시간 | 13.780ms | 12.839ms | 계산: `elapsedMs ÷ recipientCount` |
| 회원당 커넥션 점유 | 13.050ms | 12.242ms | 계산: `hikaricp_connections_usage_seconds_sum ÷ _count` = 130.83초 ÷ 10,025 / 1,227.202초 ÷ 100,245 (`-prometheus-batch.txt`, 앱 기동부터 누적. count는 회원 수 +1 / +2. 받는 사람 조회 1회, 10만은 16:00 outbox 배치 1회가 더해진 것으로 추정) |
| 커넥션 획득 대기 합계 | 0.079초 (10,025회) | 0.111초 (100,245회) | `hikaricp_connections_acquire_seconds_sum`/`_count` |
| Hikari active 최대 / pending 최대 | 1 / 0 | 1 / 0 | `-hikari.csv`, 배치 구간 안 값 있는 샘플 133개(15:44:02~15:46:14, 빈 샘플 1개) / 1,258개(15:49:44~16:11:08, 빈 샘플 0개). 계산 |
| JVM `process_cpu_usage` 최대 | 0.221 (15:44:14) = 논리 CPU 1.77개 | 0.460 (15:56:32) = 3.68개 | `-jvm.csv`, 배치 구간 안 샘플 25개(15:44:03~15:46:14) / 235개(15:49:45~16:11:04). 값은 0~1(논리 CPU 8개 전체 기준), 코어 환산 = 값 × 8(계산) |
| JVM `process_cpu_usage` 평균 | 0.0803 = 0.64개 | 0.0517 = 0.41개 | 같은 샘플. 배치 시작 60초 뒤부터만 보면 0.0650 = 0.52개(15샘플) / 0.0486 = 0.39개(224샘플) |
| 힙 사용 최대 | 177.7MiB (15:45:36) | 167.2MiB (16:10:53) | `-jvm.csv` `heap_used_bytes`, 같은 샘플. `-summary.txt`의 `heap_used_max_mb` 178 / 167과 같다 |
| GC(앱 기동부터 누적) | minor 6회, 0.068초 | minor 50회, 0.298초 | `-prometheus.txt` `jvm_gc_pause_seconds` |

샘플은 순간값이다. JVM 샘플러는 5초 간격이라 짧은 CPU 튐은 놓칠 수 있다. 배치 시작 직후(1만 15:43:57~15:44:02, 10만 15:49:42~15:49:44)는 actuator 응답이 늦어 샘플이 비어 있다(1절).

### `pg_stat_statements`

reset부터 덤프까지(앱 기동 쿼리 포함). 1만 `20261125-154322-batch-baseline-10k-0ms-pgss.txt`(문장 25개, calls 50,161, 실행 시간 합계 4,423.3ms), 10만 `20261125-154900-batch-baseline-100k-0ms-pgss.txt`(문장 27개, calls 501,258, 합계 42,039.4ms). `shared_blks_read`는 두 회차 모두 0이다(전부 캐시 적중).

| 쿼리 | 1만 calls / 합계 / 평균 | 10만 calls / 합계 / 평균 | 누가 보내나 |
|---|---|---|---|
| `insert into notification ... RETURNING` | 10,024 / 2,117.3ms / 0.211ms | 100,243 / 20,754.0ms / 0.207ms | 앱(회원당 1회) |
| `select ... from member where id=$1` (회원 재조회) | 10,024 / 884.0ms / 0.088ms | 100,243 / 8,342.2ms / 0.083ms | 앱(회원당 1회) |
| `select ... from member_device_token where member_id=$1` | 10,024 / 748.5ms / 0.075ms | 100,243 / 6,751.3ms / 0.067ms | 앱(회원당 1회. 토큰 없는 회원도 조회) |
| `SELECT $2 FROM ONLY "public"."member" x ... FOR KEY SHARE` | 10,024 / 607.3ms / 0.061ms | 100,243 / 5,670.5ms / 0.057ms | Postgres FK 검사(알림 INSERT 안에서 실행. `track=all`이라 따로 잡힘) |
| 받는 사람 조회 `select distinct m1_0.id from team_member ...` | 1 / 46.7ms | 1 / 365.5ms | 앱(배치 시작 1회) |
| `BEGIN` | 10,026 / 16.5ms | 100,245 / 152.5ms | 앱(트랜잭션마다) |
| `COMMIT` | 1 / 0.0ms | 1 / 0.0ms | 아래 설명 |

- **DB 실행 시간 비율**: 전체 합계 4,423.3ms ÷ 138,127ms = **3.2%**, 42,039.4ms ÷ 1,287,027ms = **3.3%**(계산). FK 검사는 INSERT 실행 시간 안에 이미 들어 있을 것이므로(추정: 중첩 문장은 바깥 문장 실행 중에 돈다) 빼면 3,816.0ms(2.8%) / 36,368.9ms(2.8%)다(계산).
- **회원당 Postgres 실행 시간**: 앱이 회원마다 보내는 문장(INSERT + 회원 조회 + 토큰 조회 + `BEGIN`)의 합계 ÷ 회원 수 = 3,766.3ms ÷ 10,024 = **0.376ms**, 36,000.0ms ÷ 100,243 = **0.359ms**(계산).
- **회원당 문장 수**: 위 4개 문장이 각각 회원 수와 같은 calls(`BEGIN`만 +2. 회원 외의 트랜잭션 2개로 보이나 무엇인지는 미확인)다. 회원당 앱 문장 3개 + `BEGIN` 1개, 그리고 서버 안의 FK 검사 1개다.
- **`COMMIT`이 calls 1인 이유는 미확인이다.** 회원마다 트랜잭션을 커밋하므로 `COMMIT`도 회원 수만큼 있어야 한다. pgjdbc가 커밋을 보내는 방식이나 `pg_stat_statements`의 집계 방식 때문으로 보이지만 확인하지 않았다. 그래서 회원당 왕복 수는 pgss calls만으로 셀 수 없고, 5절에서 추정한다.
- `rows`: 토큰 조회 10,548 / 105,323행(시드의 토큰 수와 같다).

## 4. 선형성과 추정

| | 1만 | 10만 | 비율 |
|---|---|---|---|
| 받는 사람 | 10,024 | 100,243 | 10.00배 |
| 전체 소요 | 138.127초 | 1,287.027초 | 9.32배 |
| 회원당 시간 | 13.780ms | 12.839ms | 0.932배 (10만이 6.8% 짧음) |
| 받는 사람 조회(Postgres) | 46.7ms | 365.5ms | 7.8배 |

계산. 출처는 3절과 같다.

- **회원 수에 비례한다.** 회원 수가 10배일 때 시간은 9.3배였다. 회원당 시간이 회원 수와 함께 커지는 구간(인덱스 크기, 힙, 알림 테이블 증가 등의 영향)은 이 두 점에서는 보이지 않았다.
- 10만이 회원당 6.8% 짧은 것은 JIT 웜업 구간(새 JVM)이 1만에서 차지하는 비중이 더 크기 때문으로 본다(**가설**). 근거: 1만의 JVM CPU 평균은 배치 전체 0.0803(25샘플)이 시작 60초 뒤부터 0.0650(15샘플)으로 내려갔다. 1회씩만 쟀으므로 회차 사이 편차일 수도 있다. 참고로 #217 컨테이너가 떠 있던 1만 스모크 2회차(`20261125-151923-*-summary.txt`)는 135,623ms, 회원당 13.53ms였다.

**100만 추정 (0ms 조건)**

- 1,000,000 × 12.839ms = **12,839초 ≈ 3시간 34분**(추정, 10만 회원당 시간). 1만 값 13.780ms로는 13,780초 ≈ 3시간 50분.
- 받는 사람 조회는 10만에서 메트릭 0.919초였다. 선형으로 늘려도 100만에서 약 9.2초(0.919 × 10, 추정) 이하로, 전체의 0.1% 미만이다.
- 받는 사람 ID 목록(`List<Long>`)을 한 번에 메모리에 올린다. 10만에서 힙 최대는 167.2MiB로 1만(177.7MiB)보다 크지 않아, 이 목록이 힙을 지배하지는 않았다. 100만이면 `Long` 객체와 참조를 합쳐 약 20~24MB로 추정한다(1,000,000 × (Long 16바이트 + 참조 4~8바이트), 추정. 힙 덤프로 확인하지 않았다).

**50ms 조건 추정 (실측하지 않음, 계약 결정 사항)**

가짜 발송기 지연은 `send()` 호출(회원 한 명)마다 한 번이고, 발송 루프가 순차라 그대로 더해진다. 계약의 식은 "0ms 실측 + 회원 수 × 50ms"다. 다만 토큰이 없는 회원은 `NotificationService.send`가 발송기를 부르지 않고 돌아가므로(`deviceTokens.isEmpty()`), 실제로 지연이 더해지는 것은 토큰 있는 회원 수다. 두 식을 함께 적는다.

| 규모 | 계약 식: 0ms 실측 + 회원 수 × 50ms | 토큰 있는 회원만: 0ms 실측 + 토큰 있는 회원 × 50ms |
|---|---|---|
| 1만 | 138.127 + 10,024 × 0.05 = 639.3초 (약 10분 39초) | 138.127 + 9,018 × 0.05 = 589.0초 (약 9분 49초) |
| 10만 | 1,287.027 + 100,243 × 0.05 = 6,299.2초 (약 1시간 45분) | 1,287.027 + 90,182 × 0.05 = 5,796.1초 (약 1시간 37분) |
| 100만 | 12,839 + 1,000,000 × 0.05 = 62,839초 (약 17시간 27분) | 12,839 + 899,634 × 0.05 = 57,820초 (약 16시간 4분) |

모두 **추정**이다. 100만의 토큰 있는 회원 899,634명은 10만의 비율(90,182 ÷ 100,243 = 0.8996)을 곱한 값이다. 50ms는 FCM 한 번의 왕복을 흉내 낸 가정값이고 실제 FCM 지연은 재지 않았다.

## 5. 병목 분석

### 5-1. 배치가 회원 한 명에게 하는 일 (코드)

`DailyQuestionOpenedNotificationBatchService`는 받는 사람 ID 전체를 한 번에 조회한 뒤 `for` 문으로 회원마다 `NotificationService.send`를 부른다(단일 스레드, 트랜잭션 없음). `send`는 회원마다 다음을 한다.

1. `NotificationPersistService.saveForActiveMember`(`@Transactional`): 커넥션 획득 → 회원 재조회(`findById`, ACTIVE 확인) → 알림 INSERT(`IDENTITY`라 즉시 실행, Postgres가 FK 검사) → 토큰 조회(`findAllByMemberId`) → 커밋 → 커넥션 반납
2. 토큰이 있으면 `pushSender.send(토큰들)`(가짜 발송기, 0ms) → `handleResults`

받는 사람 조회 쿼리가 이미 `member.status = ACTIVE`로 걸렀는데 회원을 다시 조회한다. 알림 INSERT, 토큰 조회도 회원마다 한 건씩이다.

### 5-2. 회원당 시간 분해 (10만 기준, **추정**)

| 구간 | 회원당 | 근거 |
|---|---|---|
| 전체 | 12.839ms | 계산: `elapsedMs ÷ recipientCount` |
| ├ 커넥션 점유(트랜잭션 1개) | 12.242ms (95.3%) | 계산: Hikari usage 합계 ÷ 횟수 |
| │ ├ Postgres 실행 | 0.359ms | 계산: 3절 회원당 문장 합계 |
| │ ├ 앱↔DB 왕복 | 약 5.6ms | **추정**: 왕복 4회 × `SELECT 1` 평균 1.402ms(아래) |
| │ └ 나머지(앱 처리) | 약 6.3ms | **추정**: 12.242 − 0.359 − 5.6. Spring 트랜잭션 프록시, Hibernate 조회·persist·flush, JDBC 드라이버 처리. 프로파일링은 하지 않았다 |
| └ 커넥션 밖 | 0.597ms | 계산: 12.839 − 12.242. 가짜 발송기 호출, `handleResults`, 루프·메트릭 |

1만은 같은 방식으로 전체 13.780 = 커넥션 점유 13.050(Postgres 0.376 + 왕복 약 5.6 + 나머지 약 7.1) + 커넥션 밖 0.730ms다(계산·추정).

**왕복 시간 측정** (콘솔 확인, 원시 파일 없음): 2026-11-25 약 15:47(1만 측정 직후, 10만 시드 전)에 호스트에서 `localhost:55433`(이 폴더의 Postgres)로 앱과 같은 pgjdbc 42.7.10을 써서 쟀다. 측정 코드는 scratchpad의 `RttProbe.java`(저장소 밖)다.

- 커넥션 1개, `prepareStatement("select 1")`. 워밍업 500회 뒤 autocommit 상태로 `select 1`을 n회 실행해 한 번씩 시간을 잰다.
- 이어서 autocommit을 끄고(트랜잭션 모드) 워밍업 200회 뒤, `select 1` 4번 + `commit()`을 한 묶음으로 n/4회 잰다(배치 회원 1명과 같은 모양).

| 측정 | n | 평균 | p50 | p90 | p99 |
|---|---|---|---|---|---|
| `select 1` | 4,000 | 1.402ms | 1.373ms | 2.113ms | 2.738ms |
| 트랜잭션(문장 4개 + commit) | 1,000 | 6.834ms | 7.085ms | 9.075ms | 11.834ms |

**회원당 왕복 4회로 본 근거와 한계** (추정)

- 앱이 보내는 것은 회원 조회, INSERT, 토큰 조회, 커밋이다. `BEGIN`은 pgss에 회원 수만큼 잡혔지만 pgjdbc가 첫 문장과 한 번에 보내는지(별도 왕복이 없는지)는 드라이버 소스로 확인하지 않았다(**미확인**).
- 간접 근거: 트랜잭션 측정 6.834ms는 `select 1` 1.402ms의 4.9배다. `BEGIN`이 따로 왕복했다면 6회(약 8.4ms)에 가까웠을 것이므로, 문장 4개 + 커밋 = 5회로 보는 쪽이 맞다(추정). 같은 식으로 배치는 문장 3개 + 커밋 = 4회 ≈ 5.6ms다. 트랜잭션 측정값을 4/5로 줄이면 6.834 × 4/5 = 5.5ms로 비슷하다.
- Hikari 커넥션 획득·반납과 pgjdbc `setAutoCommit` 전환이 추가 왕복을 만드는지도 확인하지 않았다(**미확인**). Hikari 획득 대기는 회원당 평균 약 1µs(0.111초 ÷ 100,245, 계산)라 풀 대기는 없다.

**앱 처리 몫을 뒷받침하는 것** (추정): 10만 배치 시작 60초 뒤부터 JVM CPU 평균은 논리 CPU 0.39개(224샘플)였다. 이것을 모두 배치 스레드 몫으로 쳐도 회원당 CPU 시간은 최대 0.39 × 12.839 ≈ 5.0ms다(계산, 상한. GC·JIT·actuator 수집 스레드도 이 안에 있다). 배치 스레드가 CPU를 쓰는 시간은 회원당 5.0ms 이하이고, 나머지 7.8ms 이상은 DB 응답 등을 기다린다(추정). 위 표의 "나머지 약 6.3ms"는 이 상한과 크게 어긋나지 않지만, 앱 처리와 대기의 정확한 비율은 프로파일러(JFR 등)로 확인해야 한다.

### 5-3. 병목 정리

1. **회원 1명 = 트랜잭션 1개 = 왕복 약 4회, 순차 실행.** 왕복과 앱 처리가 회원당 시간의 대부분이고, Postgres 실행은 2.8%다. DB를 빠르게 해도(쿼리 튜닝) 거의 줄지 않는다.
2. **자원을 거의 쓰지 않는다.** Hikari active는 배치 내내 최대 1(1,258샘플 중 1,251개가 1), pending 0이다. JVM CPU 평균은 논리 CPU 0.41개다. 커넥션 10개 중 1개, CPU 8개 중 1개 미만으로 돈다.
3. **회원마다 같은 일을 반복한다.** 회원 재조회 100,243회(받는 사람 조회에서 이미 ACTIVE 확인), 알림 단건 INSERT 100,243회, 토큰 단건 조회 100,243회(토큰 없는 회원 10,061명 포함, 이들은 발송기를 부르지 않는다).
4. **받는 사람 전체를 메모리에 올린다.** 10만에서는 힙 영향이 보이지 않았다(힙 최대 167.2MiB). 100만에서는 목록만 약 20~24MB로 추정하고, 진행 위치가 메모리에만 있어 중간에 앱이 죽으면 어디까지 보냈는지 알 수 없다(코드 기준).
5. **발송 지연이 그대로 더해진다.** 0ms에서는 발송이 커넥션 밖 0.6ms에 들어가지만, 실제 FCM 왕복이 50ms라면 토큰 있는 회원마다 50ms가 순차로 더해진다(4절: 100만 약 16~17시간, 추정).

## 6. FCM 전송 한도

출처(2026-11-25 확인, 페이지 갱신일은 미확인):
- https://firebase.google.com/docs/cloud-messaging/throttling-and-quotas
- https://firebase.google.com/docs/cloud-messaging/scale-fcm

아래 원문은 2026-11-25 두 페이지를 가져와(HTTP 200) 확인한 문장이다. 페이지에 갱신일 표시는 보이지 않았다. 두 페이지 모두 한도는 바뀔 수 있다고 적는다("The limits discussed in this section are subject to change.").

| 항목 | 원문 | 페이지 |
|---|---|---|
| 기본 전송 한도 | "The default quota of 600k messages per minute covers over 99% of FCM developers" | throttling-and-quotas |
| 〃 | "the HTTP v1 API allots 600K Quota Tokens for each 1-minute Token Bucket, which refills to full at the end of each 1-minute window." | scale-fcm |
| 세는 단위 | "The downstream quota measures messages, not requests." / 1분 창은 시계와 맞춰지지 않는다("these minutes are not aligned to the clock") | throttling-and-quotas |
| 한도 증액 | 셀프 증액 "for up to +25%"(분당 75만, 계산). 조건은 한도의 80% 이상을 하루 5분 이상 연속 사용, 클라이언트 오류 5% 미만 등이고 "no increase can be guaranteed" | throttling-and-quotas |
| 한도 초과 | "the system serves HTTP status code 429 RESOURCE_EXHAUSTED ("QUOTA_EXCEEDED") until the quota is refilled in the following minute." | throttling-and-quotas |
| 429 재시도 | "For 429 errors: retry after waiting for the duration set in the retry-after header. If no retry-after header is set, default to 60 seconds." | scale-fcm |
| 단일 기기 | "For Android, you can send up to 240 messages per minute and 5,000 messages per hour to a single device." | throttling-and-quotas |
| 최대 속도로 보내기 | "One scaling anti-pattern is to send FCM notifications as quickly as systems will allow, instead of applying server-side throttling." | scale-fcm |
| 램프업 | "At minimum, ramp from 0 to the max RPS across a 60 second time-window. Prefer longer windows for higher RPS." | scale-fcm |
| 피크 시각 회피 | "avoid sending messages within a 2 minute window of each of the :00, :15, :30, and :45 minute marks." | scale-fcm |
| 발송 분산 | "Would a 5 minute delivery window, for instance, still meet your business needs?" | scale-fcm |
| `sendEach` | 두 페이지에 `sendEach`(Admin SDK 묶음 발송) 관련 언급은 없다 | 두 페이지 |

- 메시지 수는 토큰 수로 센다고 본다(HTTP v1은 토큰 1개당 요청 1건, 추정). 회원당 토큰은 10만 시드 기준 1.051개(105,323 ÷ 100,243, 계산)라 100만 명이면 약 105만 메시지다(추정).
- **자정 발송과 권고가 충돌한다.** 이 배치의 기본 cron은 `0 0 0 * * *`(매일 00:00:00 KST, `DailyQuestionOpenedNotificationProperties.DEFAULT_CRON`)이다. 권고의 ":00 앞뒤 2분 회피"와 정면으로 겹치고, 램프업 없이 최대 속도로 시작한다. 8절 후보 8로 적는다.
- 한도는 프로젝트 단위라 같은 시각의 다른 알림(피드·댓글 등)도 같은 한도를 쓴다(추정).

## 7. 목표

계약 "결정 사항"(2026-11-25 사람 결정) 그대로다.

**(1) 서버 처리 능력: 가짜 발송기(지연 0ms) 기준 100만 건을 1분 안에 발송기로 넘긴다.**

- 필요한 속도: 1,000,000 ÷ 60초 = **초당 약 16,667명**(계산). 회원당 평균 0.06ms.
- 현재: 초당 77.9명(10만 실측), 회원당 12.839ms. **약 214배**(16,667 ÷ 77.89, 계산)가 필요하다.
- 현재 구조에서는 왕복만으로도 회원당 약 5.6ms(추정)라 순차로는 초당 약 180명(1 ÷ 5.6ms, 추정)을 넘을 수 없다. 묶음 처리(왕복을 회원 수백~수천 명에 한 번으로)와 병렬 처리가 함께 필요하다(8절).

**(2) 실제 발송: FCM 한도에 맞춰 서버에서 속도를 조절해 100만 명에게 약 2~3분 안에 전달한다(분당 60만 한도의 약 80% 이하 유지).**

- 목표 속도: 600,000 × 0.8 = **분당 480,000건 = 초당 8,000건**(계산).
- 100만 건 ÷ 48만 = 2.08분(계산, 램프업 없음).
- 램프업 60초(0 → 초당 8,000건을 직선으로 올린다고 가정)를 넣으면, 첫 60초에 240,000건(8,000 × 60 ÷ 2)을 보내고 나머지 760,000건을 1.58분에 보내 **약 2.58분**이다(추정).
- 토큰 기준(약 105만 건, 6절)이면 1분 + (1,050,677 − 240,000) ÷ 480,000분 ≈ **약 2.69분**이다(추정). 어느 쪽이든 2~3분 안이다.
- 이 속도를 지키려면 서버 처리 능력은 초당 8,000건 이상이어야 한다. 목표 (1)(초당 약 16,667명)은 이것을 여유 있게 넘는 값이다.
- "100만 명 1분 안 실제 발송"은 기본 한도(분당 60만)와 셀프 증액 최대치(분당 75만)로 모두 불가능해 목표로 두지 않았다(계약 결정 사항).

## 8. 개선 이슈 후보 (생성하지 않음, 사람 승인 뒤 생성)

기대 효과는 모두 **추정**이다. 개선 뒤에는 같은 스크립트로 1만·10만을 다시 재고, 100만은 개선 후 실측한다(계약 결정 사항).

### 후보 1. 받는 사람을 커서/청크로 읽는다
- **문제**: 받는 사람 ID 전체를 `List<Long>`으로 한 번에 읽고, 진행 위치는 메모리에만 있다.
- **근거**: 받는 사람 조회 1만 46.7ms / 10만 365.5ms(pgss), 메트릭 0.899 / 0.919초. 10만 힙 최대 167.2MiB.
- **기대 효과**: 시간 단축은 작다(전체의 0.1% 미만). 청크(예: `id > 마지막 id` 순 1,000명)가 아래 후보 2~6의 묶음 단위가 되고, 마지막 처리 ID로 이어서 보내기(후보 7)의 기준점이 된다. 100만 목록 메모리 약 20~24MB(추정)를 청크 크기로 줄인다.

### 후보 2. 알림을 묶어서 저장한다 (batch insert)
- **문제**: 알림 INSERT가 회원마다 1건, 1왕복이다. `Notification` id가 `IDENTITY`라 Hibernate의 JDBC 배치 INSERT가 적용되지 않는다(Hibernate 동작 기준, 이 코드에서 실행으로 확인하지 않음 → 미확인).
- **근거**: 10만 INSERT 100,243회, 20,754.0ms(앱 문장 DB 시간 36,000.0ms의 57.7%), 평균 0.207ms + 회원당 왕복 1회(약 1.4ms, 추정). FK 검사 5,670.5ms가 따로 붙는다.
- **기대 효과**: 청크 1,000명이면 INSERT 왕복이 100만 회 → 1,000회. 푸시 data에 `notificationId`를 넣으므로 `RETURNING id`로 id를 받아야 한다.

### 후보 3. 디바이스 토큰을 한 번에 조회한다
- **문제**: 토큰 조회가 회원마다 1건이고, 토큰 없는 회원(10%)도 조회한다.
- **근거**: 10만 100,243회, 6,751.3ms, 평균 0.067ms + 회원당 왕복 1회. 토큰 없는 회원 10,061명.
- **기대 효과**: 청크마다 `member_id IN (...)` 1회로 회원당 왕복 1회 제거.

### 후보 4. 회원 재조회를 없앤다
- **문제**: 받는 사람 조회가 이미 ACTIVE 회원만 고르는데, 저장 전에 회원을 다시 조회한다.
- **근거**: 10만 100,243회, 8,342.2ms, 평균 0.083ms + 회원당 왕복 1회.
- **기대 효과**: 회원당 왕복 1회 제거. 조회와 발송 사이에 탈퇴한 회원을 거르는 역할은 청크 단위 상태 확인(IN 1회)이나 INSERT 조건으로 옮긴다. 엔티티 참조만 필요하면 `getReference`로 바꿀 수 있다(검토 필요).

후보 2~4를 합치면 회원당 왕복 4회가 청크당 몇 회로 줄고, 트랜잭션·Hibernate 처리(약 6.3ms 몫, 추정)도 청크 단위로 나뉜다. 그래도 목표 (1)의 214배를 순차 처리만으로 낼 수 있을지는 미확인이라 후보 6과 함께 본다.

### 후보 5. `sendEach`로 500건씩 묶어 보낸다
- **문제**: 발송기를 토큰 있는 회원마다 한 번 부른다(10만에서 90,182회).
- **근거**: 0ms 조건에서는 커넥션 밖 회원당 0.6ms라 영향이 작지만, 50ms 조건 추정에서는 100만 약 16~17시간 중 45,000~50,000초가 발송 대기다(4절).
- **기대 효과**: 500 토큰씩이면 10만 시드의 105,323 토큰이 211회 호출(105,323 ÷ 500 올림, 계산). `sendEach`의 한 번 최대 500건과 내부 동작(메시지마다 HTTP 요청인지)은 Admin SDK 문서로 확인해야 한다(**미확인**, FCM 한도 페이지에는 언급 없음). 결과 크기·순서 규약(`PushSender.java:18`)은 유지해야 한다.

### 후보 6. 병렬로 처리한다
- **문제**: 단일 스레드라 커넥션 1개, CPU 1개 미만만 쓴다.
- **근거**: Hikari active 최대 1, pending 0(10만 1,258샘플). JVM CPU 평균 논리 CPU 0.41개(235샘플).
- **제약**: 풀 10은 같은 앱의 API 요청과 나눠 쓴다. dev DB(Supabase 세션 풀러)는 슬롯이 15개다(이슈 #215 본문에 적힌 값. 이 문서에서 직접 확인하지 않음). 운영 DB의 연결 제한은 미확인이다. 배치 병렬도는 풀의 일부(예: 2~4)로 제한하고, 발송(HTTP)은 커넥션 밖에서 병렬로 둔다.
- **기대 효과**: 청크 단위 처리 위에서 병렬도 N이면 대략 N배(추정. DB CPU 2개 제한과 FCM 한도가 다음 상한).

### 후보 7. FCM 속도 조절, 429 재시도, 이어서 보내기, 중복 발송 방지
- **문제**: 지금은 속도 조절이 없다. 목표 (1)의 속도(분당 100만 명)로 그대로 보내면 FCM 한도(분당 60만)를 넘어 429를 받는다(추정). 재시도·진행 기록이 없어 중간에 실패하거나 앱이 재시작되면 나머지가 안 가거나, 다시 돌리면 이미 받은 회원에게 또 간다(코드 기준 추정. 알림 중복을 막는 DB 제약이 있는지는 확인하지 않았다 → 미확인).
- **근거**: 6절 한도(분당 60만, 429 `QUOTA_EXCEEDED`, `retry-after` 없으면 60초, 램프업 60초 이상).
- **기대 효과**: 토큰 버킷 등으로 초당 8,000건(분당 48만) 이하 유지, 60초 램프업, 429에서 `retry-after`(없으면 60초) 후 재시도, 청크 진행 기록으로 이어서 보내기, 회원·날짜 단위 중복 방지. 목표 (2)의 전제다.

### 후보 8. 발송 시각을 자정에서 비키거나 나눈다
- **문제**: 기본 cron이 매일 00:00:00(`DEFAULT_CRON = "0 0 0 * * *"`)이라 FCM 권고(:00 앞뒤 2분 회피, 램프업)와 충돌한다.
- **기대 효과**: 시작을 00:03 등으로 옮기거나 5분 창 안에서 나눠 보내 FCM 쪽 지연·429 위험을 줄인다(추정). "오늘의 질문이 자정에 열린다"는 제품 규칙과의 관계는 사람이 정한다.

### 후보 9. `handleResults`의 결과·토큰 길이 가드
- #213 critic에서 미룬 항목이다. 발송기 결과 리스트 길이가 토큰 리스트와 다를 때의 가드다. 계약 결정 사항에 따라 이 측정과 별도 이슈로 낸다. 이번 측정에서는 가짜 발송기가 크기·순서 규약을 지켰고(토큰별 결과 합계 = 토큰 수, 3절) 영향은 없었다.

## 9. 남은 리스크·미확인

- **왕복 시간이 운영과 다르다.** 측정 환경은 Windows Docker Desktop의 `localhost` 포트 포워딩이라 `select 1` 평균 1.402ms다(콘솔 확인). 운영은 앱 ↔ Supabase(네트워크 경유)라 왕복이 더 길거나 짧을 수 있다(미확인). 회원당 왕복 4회 구조이므로 운영 왕복이 길수록 운영 배치는 더 느리다(추정).
- **1회 측정이다.** 규모마다 1회씩만 쟀다. 회차 간 편차는 1만 스모크 2회차(13.53ms)와 정식(13.78ms) 비교 정도만 있다.
- **호스트 외부 부하 기록이 없다.** #227 샘플러 도입 전이다. README 10-5에 #217 쪽 샘플러 시험 하나(15초)가 "#215 측정 시간대"에 돌았다고 적혀 있으나 시각이 없어, 정식 두 회차와 겹쳤는지는 미확인이다.
- **1만 회차 Prometheus 데이터는 없다.** 10만 준비의 `down -v`로 지워졌다. 측정 회차의 수치는 원시 파일(`-summary.txt`, `-prometheus*.txt`, `-hikari.csv`, `-jvm.csv`, `-pgss.*`, `-app.log`, `-seed.log`)에서 가져왔다. 예외는 "콘솔 확인"으로 표시한 값(왕복 시간 측정, 10만 시드의 `normal_teams`·실행 시간, 실행 명령), 작성 시점에 조회한 호스트 정보(`Win32_*`), #217 세션이 알려 준 부하 시각이다.
- **100만은 시드도 실측도 하지 않았다.** 100만 수치는 모두 선형 추정이다. 시드 시간·디스크 용량, `00_common.sql` 팀 이름 잘림(team_id ≥ 100000)의 영향도 미확인이다(계약 남은 리스크).
- **JIT 웜업 포함.** 매 회차 새 JVM이고 웜업을 하지 않았다. 웜업 몫을 따로 재지 않았다.
- **회원당 시간 분해의 "나머지"(앱 처리 약 6.3ms)는 프로파일링하지 않은 추정이다.** pgjdbc의 `BEGIN`·`COMMIT` 전송 방식, `COMMIT`이 pgss에 1회만 잡힌 이유, Hikari·`setAutoCommit`의 추가 왕복 여부도 미확인이다.
- **측정 당시 Postgres 실제 설정·버전 문자열과 호스트 여유 메모리는 이번 회차 원시 파일에 없다.** compose 설정값만 적었다.
- FCM 한도 페이지의 갱신일은 미확인이다(2026-11-25 가져온 내용에 표시가 없었다, 6절).

## 10. 원시 파일 목록 (`loadtest/results/raw/`)

| 접두사 | 내용 |
|---|---|
| `20261125-154322-batch-baseline-10k-0ms-*` | 1만 정식 측정: `summary.txt`, `app.log`, `seed.log`, `docker-ps.txt`, `hikari.csv`, `jvm.csv`, `pgss.txt`/`.csv`, `prometheus.txt`, `prometheus-batch.txt` |
| `20261125-154900-batch-baseline-100k-0ms-*` | 10만 정식 측정: 같은 구성 |
| `20261125-151427-batch-smoke-10k-0ms-*`, `20261125-151923-batch-smoke-10k-0ms-*` | 스크립트 스모크(1만, #217 컨테이너 떠 있음). 베이스라인 아님 |

원시 파일이 없는 값: 10만 `00_common.sql` 소요(real 12.9초), 10만 준비 때 40번 단독 실행(real 6.4초), 10만 `normal_teams=23340`, 실행 명령 줄, 왕복 시간 측정 결과(`RttProbe`). 모두 콘솔 확인이다.
