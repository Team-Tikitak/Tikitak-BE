# loadtest — 조회 API 부하 측정 환경

로컬 Docker Postgres에 대량 시드를 넣고, 같은 조건에서 조회 API를 측정하기 위한 자산이다.
#217(피드·알림 조회 API 베이스라인)에서 만들었고, #215도 같은 환경과 공통 시드를 쓴다.

**dev Supabase나 prod DB에 접속하거나 시드를 넣지 않는다.** 이 디렉터리의 모든 것은 로컬 컨테이너 전용이다.

## 구성

```
loadtest/
├── docker-compose.yml        # Postgres 17.6 + Redis + Prometheus + Grafana (프로젝트 이름 tikitak-loadtest)
├── initdb/                   # 볼륨이 비어 있을 때 한 번 실행 (pg_stat_statements 확장 생성)
├── .env.example              # 앱 실행용 env 템플릿 (가짜 R2·Kakao·OAuth 값, actuator 포트 8081 분리)
├── .env                      # 위 파일을 복사해 만든다. 커밋하지 않는다
├── sql/
│   ├── 00_common.sql         # 공통 시드: member, team, team_member (#217, #215 공유)
│   ├── 10_feed.sql           # 피드 시드: place, feed, feed_image, feed_tag, feed_reaction, feed_comment (#217)
│   ├── 20_notification.sql   # 알림 시드: notification (#217)
│   ├── 30_targets.sql        # k6 입력 JSON 추출 (읽기 전용, #217)
│   └── NN_<기능>.sql          # 기능별 시드. 00_common 다음에 번호 순서대로 실행한다
├── k6/
│   ├── read-api-baseline.js  # 조회 API 측정 시나리오 (mixed / ramp / solo, #217)
│   └── data/                 # 시드에서 뽑은 k6 입력(targets.json, comment-feeds.json). 커밋하지 않는다
├── scripts/
│   ├── extract-comment-feeds.sh  # 댓글 요청 대상(팀별 최신 피드 20개) 추출
│   ├── pgss.sh               # pg_stat_statements 초기화·덤프
│   ├── hikari-sampler.sh     # /actuator/prometheus의 Hikari 지표를 1초마다 CSV로
│   └── run-measure.sh        # 측정 전 웜업 + 위를 묶은 측정 1회 실행 래퍼
├── prometheus/prometheus.yml # 앱 management 포트(8081)를 1초 간격으로 수집
├── grafana/
│   ├── provisioning/         # 데이터소스(Prometheus)와 대시보드 자동 로드 설정
│   └── dashboards/           # Tikitak Backend 대시보드 JSON (운영 대시보드를 로컬용으로 고친 복사본)
└── results/                  # 결과 문서. results/raw/ 는 원시 로그로 커밋하지 않는다
```

시드는 `00_common.sql`이 회원·팀·팀원을 만들고, 그다음 기능별 파일(`10_...`, `20_...`)이 그 위에 데이터를 쌓는 구조다.
#215 등 다른 작업은 `00_common.sql`을 그대로 쓰고, 자기 기능의 시드 파일만 번호를 붙여 추가한다.
기능별 시드는 `00_common.sql`이 만든 팀 구조(아래 "공통 시드가 만드는 것")를 전제로 한다.

## 고정 환경값

측정 도중에는 아래 값을 바꾸지 않는다. 바꿔야 하면 결과 문서에 바뀐 값과 이유를 적고 처음부터 다시 측정한다.

| 항목 | 값 | 위치 |
|---|---|---|
| Postgres 이미지 | `postgres:17.6-alpine` (dev Supabase `server_version` 17.6에 맞춤) | `docker-compose.yml` |
| Postgres 리소스 제한 | CPU 2개(`cpus: 2`), 메모리 2GB(`mem_limit: 2g`), `/dev/shm` 256MB | `docker-compose.yml` |
| `shared_buffers` | 512MB (메모리 제한의 1/4) | `docker-compose.yml` command |
| `effective_cache_size` | 1536MB (메모리 제한의 3/4) | 〃 |
| `work_mem` | 4MB (Postgres 기본값을 명시) | 〃 |
| `maintenance_work_mem` | 256MB (시드 후 인덱스·ANALYZE 속도용) | 〃 |
| `max_wal_size` | 4GB (대량 시드 중 checkpoint 빈도를 줄임) | 〃 |
| `max_connections` | 100 (기본값을 명시) | 〃 |
| `shared_preload_libraries` | `pg_stat_statements` (`track=all`, `max=10000`) | 〃 |
| `track_io_timing` | on (`EXPLAIN (ANALYZE, BUFFERS)`에 I/O 시간이 나오게) | 〃 |
| 타임존 | 컨테이너 `TZ`·`PGTZ`와 서버 `timezone` 모두 `Asia/Seoul` | 〃 |
| Redis | `redis:7.4-alpine`, CPU 1개, 메모리 256MB | 〃 |
| Prometheus | `prom/prometheus:v3.5.0`, CPU 0.5개, 메모리 512MB, 수집 간격 1초(`scrape_timeout` 1초), 보존 30일 | `docker-compose.yml`, `prometheus/prometheus.yml` |
| Grafana | `grafana/grafana:12.1.1`, CPU 0.5개, 메모리 512MB | `docker-compose.yml` |
| 호스트 포트 | Postgres **55432**, Redis **56379** (로컬의 5432/6379와 겹치지 않게), Prometheus **59090**, Grafana **53000** (둘은 `127.0.0.1`에만 열림) | 〃 |
| 컨테이너·볼륨 | `tikitak-loadtest-{postgres,redis,prometheus,grafana}`, 볼륨 `tikitak-loadtest-pgdata`, `tikitak-loadtest-prometheus`, `tikitak-loadtest-grafana` | 〃 |
| 앱 | `./gradlew bootJar` 결과 jar를 `java -Duser.timezone=Asia/Seoul -jar`로 실행. 프로필 `dev`. devtools 안 씀 | 아래 절차 |
| 앱 포트 | API **8080**, actuator **8081**(`MANAGEMENT_SERVER_PORT=8081`) | `.env.example` |
| Hikari 풀 | `SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=10` | `.env.example` |
| 측정 전 웜업 | 앱 기동 후 매 측정 전 mixed 비중 100 RPS로 2분 (8-0 참고) | `scripts/run-measure.sh` |

Prometheus·Grafana의 CPU 제한(합계 1개)은 Postgres 제한(2개)과 별개로 호스트 CPU에서 나온다. 앱·k6도 같은 호스트 CPU를 쓰므로 측정 도중에는 두 컨테이너의 제한도 바꾸지 않는다.

실제로 적용된 값은 아래로 확인한다(결과 문서에는 이 출력을 옮긴다).

```bash
docker compose -f loadtest/docker-compose.yml exec -T postgres psql -U tikitak -d tikitak -c "
  SELECT version();
  SELECT name, setting, unit FROM pg_settings
  WHERE name IN ('shared_buffers','effective_cache_size','work_mem','maintenance_work_mem',
                 'max_connections','max_wal_size','shared_preload_libraries','timezone','track_io_timing');"
docker inspect tikitak-loadtest-postgres --format 'NanoCpus={{.HostConfig.NanoCpus}} Memory={{.HostConfig.Memory}}'
```

## 절차

명령은 모두 저장소 루트에서 실행한다. Docker Desktop이 켜져 있어야 한다.

### 1. DB·Redis·모니터링 기동

```bash
docker compose -f loadtest/docker-compose.yml up -d --wait
```

`--wait`는 네 컨테이너(postgres, redis, prometheus, grafana)가 healthy가 될 때까지 기다린다. 모니터링은 9번을 본다. 볼륨이 비어 있으면 `initdb/01_pg_stat_statements.sql`이 실행돼 `pg_stat_statements` 확장이 만들어진다.

```bash
docker compose -f loadtest/docker-compose.yml exec -T postgres psql -U tikitak -d tikitak -Atc "SELECT extname FROM pg_extension"
# plpgsql, pg_stat_statements 가 보여야 한다
```

### 2. 앱 jar 빌드

```bash
./gradlew bootJar
ls build/libs/   # tikitak-0.0.1-SNAPSHOT.jar (이름의 버전은 build.gradle의 version을 따른다)
```

### 3. env 준비

```bash
cp loadtest/.env.example loadtest/.env
```

`.env.example`의 값은 모두 가짜이고, 그대로 두면 로컬 compose에 맞는다. 값에 공백이나 따옴표를 넣지 않는다.
모니터링 추가(커밋 4) 전에 복사한 `.env`에는 `MANAGEMENT_SERVER_PORT=8081`이 없다. 다시 복사하거나 그 줄을 추가한다. 없으면 actuator가 8080에 그대로 있어 스크립트·Prometheus가 앱을 찾지 못한다.
Windows에서 `.env`를 편집기로 열어 저장했다면 줄 끝이 CRLF가 되지 않았는지 확인한다(Git Bash의 `source`는 값 끝에 `\r`을 붙인다).

### 4. 앱 실행 (Flyway가 V1~V25를 적용한다)

JDK 21 이상이 필요하다. 앱은 API에 포트 8080, actuator(`/actuator/health`, `/actuator/prometheus`)에 포트 8081을 쓴다.
actuator를 분리하는 이유: management 포트는 별도 Tomcat(스레드 이름 `http-nio-8081-exec-*`)이라, API 요청이 몰려 8080 스레드가 밀려도 Prometheus 수집과 Hikari 샘플러가 응답을 받는다.
앱의 `SecurityFilterChain`은 8081에도 걸리지만 `/actuator/**`가 화이트리스트라 토큰 없이 열린다(2026-11-24 확인: 8081 `/actuator/prometheus` 200, 8081 `/foo` 401, 8080 `/actuator/health` 404).

**Git Bash**

```bash
set -a; source loadtest/.env; set +a
java -Duser.timezone=Asia/Seoul -jar build/libs/tikitak-0.0.1-SNAPSHOT.jar
```

백그라운드로 돌리고 로그를 파일로 남기려면:

```bash
mkdir -p loadtest/results/raw
( set -a; source loadtest/.env; set +a
  nohup java -Duser.timezone=Asia/Seoul -jar build/libs/tikitak-0.0.1-SNAPSHOT.jar \
    > loadtest/results/raw/app.log 2>&1 & echo $! > loadtest/results/raw/app.pid )
# 종료: kill "$(cat loadtest/results/raw/app.pid)"
```

**PowerShell**

```powershell
Get-Content loadtest/.env | Where-Object { $_ -match '^\s*[A-Za-z_][A-Za-z0-9_]*=' } | ForEach-Object {
  $name, $value = $_ -split '=', 2
  [Environment]::SetEnvironmentVariable($name.Trim(), $value, 'Process')
}
java "-Duser.timezone=Asia/Seoul" -jar build/libs/tikitak-0.0.1-SNAPSHOT.jar
```

PowerShell은 env를 현재 세션에만 넣는다. 값이 빈 항목(`REDIS_PASSWORD`, `FIREBASE_CREDENTIALS_BASE64`)은 설정되지 않은 것으로 처리되고, 앱 기본값(빈 문자열)이 쓰인다.

### 5. 기동·Flyway 확인

로그에 `Successfully applied 25 migrations`(빈 DB일 때)와 `Started TikitakApplication`이 나와야 한다.

```bash
curl -s localhost:8081/actuator/health
# {"groups":["liveness","readiness"],"status":"UP"}

docker compose -f loadtest/docker-compose.yml exec -T postgres psql -U tikitak -d tikitak -Atc \
  "SELECT count(*) FILTER (WHERE success), max(version::int) FROM flyway_schema_history"
# 25|25

curl -s localhost:8081/actuator/prometheus | grep -E '^hikaricp_connections_(max|active|pending)'
# hikaricp_connections_max{...} 10.0

curl -s localhost:59090/api/v1/targets | grep -o '"health":"[a-z]*"'
# "health":"up"   (Prometheus가 앱을 수집하고 있다)
```

### 6. 공통 시드

앱이 Flyway를 적용한 **빈 DB**에서 한 번만 실행한다. 호스트에 psql이 없어도 되도록 컨테이너 안의 psql을 쓴다.

```bash
time docker compose -f loadtest/docker-compose.yml exec -T postgres \
  psql -U tikitak -d tikitak -v normal_teams=2000 -f - < loadtest/sql/00_common.sql
```

- `-v normal_teams=N`: 일반 팀 수(기본 2000). 큰 팀 3개는 항상 만든다.
- `-v seed=X`: `setseed` 값(-1.0~1.0, 기본 0.217). 같은 값이면 같은 데이터가 나온다.
- 이미 데이터가 있으면 아무것도 넣지 않고 에러로 끝난다(psql 종료 코드 3). 다시 넣으려면 7번으로 초기화한다.
- 실행이 끝나면 확인 쿼리 결과(건수, 팀별 멤버 수 분포, 소속 팀 수 분포, 큰 팀 멤버, 무결성 검사, 프로필 이미지 비율)가 출력된다.

기능별 시드(`10_...` 이후)는 같은 방식으로 번호 순서대로 실행한다.

#### 공통 시드가 만드는 것

| 대상 | 내용 |
|---|---|
| 큰 팀 | `team.id` 1~3, 이름 `loadtest-big-1..3`, `description = 'loadtest:big'`, 멤버 6명 |
| 일반 팀 | `team.id` 4~, 이름 `loadtest-team-NNNNN`, `description = 'loadtest:normal'`, 멤버 3~6명 |
| team_member | 팀마다 slot 1이 OWNER(팀당 1명), 나머지 MEMBER, 모두 ACTIVE. `last_activity_checked_at` 채움. `profile_img_url` 약 50% |
| member | ACTIVE, `onboarding_completed=true`, `profile_character_type` 채움, 약관·개인정보 동의, `active_team_id`는 주 소속 팀 |
| 여러 팀 소속 | 큰 팀 전부와 일반 팀 약 20%의 마지막 멤버는 다른 팀 회원이 겸한다. 이 회원들은 2개 팀에 속한다 |

ID는 명시적으로 넣고 identity 시퀀스를 마지막 값으로 맞춘다. 시각 컬럼은 `Asia/Seoul` 기준 로컬 시각이다(앱이 `-Duser.timezone=Asia/Seoul`로 저장하는 값과 같은 기준).

#### 실행 기록 (2026-11-24, `normal_teams=2000`, `seed=0.217`)

| 항목 | 값 |
|---|---|
| 시간 | 약 2초 (`time` real 1.99s / 2.08s) |
| team | 2,003 (큰 팀 3) |
| member | 8,557 |
| team_member | 8,956 |
| 일반 팀 멤버 수 분포 | 3명 527팀, 4명 500팀, 5명 481팀, 6명 492팀 |
| 소속 팀 수 | 1개 팀 8,158명, 2개 팀 399명 |
| 무결성 검사 | OWNER≠1 팀 0, 중복 팀원 0, 잘못된 active_team 0, 잘못된 회원 상태 0 |
| `profile_img_url` 채움 비율 | 0.506 |

### 6-1. 피드·알림 시드 (#217)

`00_common.sql` 다음에 `10_feed.sql` → `20_notification.sql` 순서로 실행한다. 두 파일 모두 한 트랜잭션으로 넣고, 커밋 뒤 `VACUUM (ANALYZE)`와 확인 쿼리를 실행한다.
시드가 도는 동안(약 7~8분)은 앱을 끄는 것을 권장한다. 앱의 배치가 같은 테이블을 건드리지 않게 하고, 시드가 CPU 2개를 다 쓰기 때문이다.

```bash
time docker compose -f loadtest/docker-compose.yml exec -T postgres \
  psql -U tikitak -d tikitak -f - < loadtest/sql/10_feed.sql
time docker compose -f loadtest/docker-compose.yml exec -T postgres \
  psql -U tikitak -d tikitak -f - < loadtest/sql/20_notification.sql
```

- 대상 테이블(`10_feed`: place, feed, feed_image, feed_tag, feed_reaction, feed_comment / `20_notification`: notification)에 행이 있으면 아무것도 넣지 않고 에러로 끝난다. 다시 넣으려면 7번으로 초기화한다.
- `-v salt=N`: 난수 salt(정수, 기본 217). 난수는 `random()`이 아니라 `hashint8extended(key, salt)`로 만들어 실행 계획과 관계없이 같은 값이 나온다. 다만 시각은 실행 시점의 `LOCALTIMESTAMP` 기준이라, 날짜 경계에 걸리는 데일리 답변 수는 실행일마다 조금 달라질 수 있다.
- `10_feed.sql` 변수: `places`(기본 5000), `normal_avg`(일반 팀 팀당 평균 피드, 기본 350), `big_feeds`(큰 팀 팀당 피드, 기본 100000).
- `20_notification.sql` 변수: `min_per`(기본 200), `max_per`(기본 2000), `days`(기본 90).
- 두 파일은 트랜잭션 안에서만 `work_mem=128MB`, `session_replication_role=replica`(FK 트리거 끔)를 쓴다. FK 트리거를 켜 두면 INSERT가 크게 느려져서(소규모 실측: 반응 7.2초→0.8초, 댓글 15초→1.9초) 끄고, 대신 확인 쿼리의 "FK 고아 검사"로 FK가 실제 행을 가리키는지 확인한다(`feed_image.media_id`는 전부 NULL이라 제외). 서버 설정은 바꾸지 않는다.

#### 피드·알림 시드가 만드는 것

| 대상 | 내용 |
|---|---|
| place | 5,000개. `external_place_id` = `10000000 + id`(유니크). 주소는 카카오 지번 형식(`서울 강남구 대학로 12`), `region`은 `FeedService.extractRegion(address)` 결과와 같은 값(`서울 강남구`, `경기 성남시 분당구`, `세종특별자치시`, `대구` 등 36개 지역, 서울에 쏠림) |
| feed | 큰 팀 팀당 100,000건, 일반 팀 팀당 평균 350건(지수분포, 최소 10건). 최근 730일. 팀마다 5%(최소 1건)는 이번 달(KST). ID는 created_at 순. soft delete 없음 |
| 데일리 답변 | 피드 21%를 후보로 뽑고 (팀, 작성자, 날짜)마다 1건만 답변으로 만든다. 질문은 `DailyQuestionService.resolveTodayQuestion`과 같은 규칙, `daily_answer_active_key` = `teamId:teamMemberId:questionId:yyyy-MM-dd`. 이미지 1장, 작성자 태그만, place 없음(실제 생성 경로와 같음). 큰 팀은 작성자 6명 × 날짜 수가 상한이라 약 4%다 |
| place 지정 | 전체 피드의 약 60%. 팀마다 후보 장소(큰 팀 300개, 일반 팀 5~30개) 중 `floor(K * u^3)` 순위로 골라 인기 장소에 몰린다. 큰 팀은 최다 장소에 약 9,000건 |
| feed_image | 일반 피드 1~3장(50/30/20%), `order_index` 0부터. `media_id`·`width`·`height`는 NULL. `img_url` = `https://loadtest-media.invalid/media/feed-image/<uuid>.jpg`(데일리 피드는 `media/daily-question-image/`)(`.env.example`의 `R2_PUBLIC_BASE_URL` 아래라 `ImageUrlResolver`가 preset을 붙인다) |
| feed_tag | 작성자 + 같은 팀 다른 멤버 0~2명 |
| feed_reaction | 0~4개, (피드, 멤버) 유니크, `reaction_type` 6종 |
| feed_comment | 피드 80%에 0~3개, 피드 0.1%(약 1,000건, 약 30%는 큰 팀)는 500~2,000개. 작성자는 같은 팀 멤버, `feed_image_id`는 그 피드의 이미지, position 0~1. 대댓글 컬럼은 스키마에 없다 |
| notification | 회원당 200~2,000건(합계 약 1,000만). 회원이 속한 팀의 최근 90일 피드를 가리키고, `created_at`은 그 피드 뒤 0~6시간. type·title·body는 `NotificationEventListener`와 같은 문구, actor는 같은 팀의 다른 멤버. 읽음 약 70% |

### 6-2. k6 대상 추출

`30_targets.sql`은 DB를 바꾸지 않고 k6 입력 JSON 하나를 stdout으로 낸다. `-At`로 실행한다.

```bash
mkdir -p loadtest/k6/data
docker compose -f loadtest/docker-compose.yml exec -T postgres \
  psql -U tikitak -d tikitak -At -f - < loadtest/sql/30_targets.sql > loadtest/k6/data/targets.json
```

| 키 | 내용 |
|---|---|
| `teams[]` | 모든 팀. `teamId`, `isBig`, `members[{memberId, teamMemberId}]`, `places[{externalPlaceId, feedCount}]`(피드 수 내림차순. 큰 팀은 상위 100개, `-v big_top_places=N`으로 변경) |
| `commentHeavyFeeds[]` | 댓글 500개 이상인 피드. `teamId`, `feedId`, `commentCount`, `memberId`(작성자) |
| `deepCursors[]` | 큰 팀별 피드 목록의 10,000·50,000·90,000번째 피드. `teamId`, `position`, `cursor`(`FeedService.encodeCursor`와 같은 `LocalDateTime.toString() + "_" + id`. 초·소수점이 0이면 생략하는 Java 규칙을 따른다) |

파일 크기는 약 2MB다. `loadtest/k6/data/`는 커밋하지 않는다.

### 6-3. 시드 확인 쿼리

계약 완료 조건을 한 번에 확인한다. 각 시드 파일도 끝에서 더 자세한 확인 쿼리를 출력한다.

```bash
docker compose -f loadtest/docker-compose.yml exec -T postgres psql -U tikitak -d tikitak -x -c "
SELECT
  (SELECT count(*) FROM feed) AS feeds,                                               -- 900,000 ~ 1,100,000
  (SELECT string_agg(team_id || ':' || c, ', ' ORDER BY team_id) FROM (
     SELECT team_id, count(*) AS c FROM feed WHERE team_id IN (SELECT id FROM team WHERE description = 'loadtest:big')
     GROUP BY team_id) x) AS big_team_feeds,                                            -- 각 99,000 ~ 101,000
  (SELECT count(*) FROM feed WHERE created_at >= date_trunc('month', LOCALTIMESTAMP)) AS this_month_feeds,  -- > 0
  (SELECT round(avg((created_at >= date_trunc('month', LOCALTIMESTAMP))::int), 3) FROM feed) AS this_month_ratio,  -- 약 0.05
  (SELECT round(avg((question_id IS NOT NULL)::int), 3) FROM feed) AS daily_ratio,
  (SELECT count(*) FROM feed f WHERE NOT EXISTS (
     SELECT 1 FROM feed_tag t WHERE t.feed_id = f.id AND t.team_member_id = f.team_member_id)) AS feeds_without_author_tag,  -- 0
  (SELECT count(*) FROM (SELECT 1 FROM feed_image GROUP BY feed_id, order_index HAVING count(*) > 1) x) AS dup_image_order,  -- 0
  (SELECT count(*) FROM (SELECT 1 FROM feed_comment GROUP BY feed_id HAVING count(*) >= 500) x) AS feeds_with_500_comments,  -- > 0
  (SELECT round(avg((place_id IS NOT NULL)::int), 3) FROM feed) AS place_ratio,     -- 약 0.6
  (SELECT max(c) FROM (SELECT count(*) AS c FROM feed WHERE place_id IS NOT NULL GROUP BY team_id, place_id) x) AS max_feeds_per_place,
  (SELECT count(*) FROM notification) AS notifications,
  (SELECT min(c) || ' ~ ' || max(c) FROM (SELECT count(*) AS c FROM notification GROUP BY member_id) x) AS notifications_per_member,  -- 200 ~ 2000
  (SELECT count(*) FROM member m WHERE NOT EXISTS (SELECT 1 FROM notification n WHERE n.member_id = m.id)) AS members_without_notification,  -- 0
  (SELECT round(avg(is_read::int), 3) FROM notification) AS read_ratio,             -- 0.6 ~ 0.8
  (SELECT round(min(r), 3) || ' ~ ' || round(max(r), 3) FROM (
     SELECT avg(is_read::int) AS r FROM notification GROUP BY member_id) x) AS read_ratio_per_member,
  pg_size_pretty(pg_database_size(current_database())) AS db_size;"
```

이 확인 쿼리는 알림 1,000만 건을 집계하므로 1~2분 걸린다.

#### 실행 기록 (2026-11-24, 기본 변수, `salt=217`)

두 번 실행했다. 1회차는 00_common이 들어 있던 DB에 10→20→30을 실행했다. 2회차는 `down -v` → `up -d --wait` → 앱 기동(Flyway 25건) → 앱 종료 → 00→10→20→30 순서로 실행했다. 시간은 확인 쿼리와 `VACUUM (ANALYZE)`까지 포함한 psql 실행 시간이다.

| 항목 | 1회차 | 2회차 |
|---|---|---|
| `10_feed.sql` | 2분 59초 | 3분 27초 |
| `20_notification.sql` | 3분 57초 | 4분 56초 |
| `30_targets.sql` | 6초 | 5초 |
| 00~30 합계 | - | 8분 30초 (00_common 1초 포함) |
| 처음부터 전체 재현(초기화 → 앱 기동 → 시드) | 미측정 | 미측정 (위 2회차의 기동·Flyway 시간은 따로 재지 않았다) |

2회차 결과(1회차와 피드·알림 건수는 같고, 데일리 답변 수만 148,958 → 148,967로 다르다. 실행 시각이 달라 날짜 경계에 걸리는 피드가 바뀌기 때문이다):

| 항목 | 값 |
|---|---|
| place | 5,000 |
| feed | 1,000,287 (큰 팀 각 100,000, 일반 팀 700,287: 팀당 최소 10, 평균 350, 최대 2,961) |
| 이번 달 피드 | 50,104 (비율 0.050, 큰 팀 각 5,000. 이번 달 피드가 없는 팀 0) |
| 데일리 답변 비율 | 전체 0.149, 일반 팀 0.195, 큰 팀 0.041 (팀당 약 4,095건. 작성자 6명 × 2025-01-01 이후 날짜 수가 상한) |
| place 있는 피드 비율 | 0.600 (큰 팀 장소 277~287개, 최다 장소 피드 8,879~8,988건) |
| feed_image | 1,595,242 (1장 약 57%, 2장 약 26%, 3장 약 17%) |
| feed_tag | 1,596,473 |
| feed_reaction | 1,962,919 |
| feed_comment | 2,434,007 (댓글 500개 이상 피드 995건, 그중 큰 팀 306건) |
| notification | 10,003,910 (회원당 200~2,000, 평균 1,169. 알림 없는 회원 0) |
| 읽음 비율 | 전체 0.700, 회원별 0.640~0.774 |
| type 비율 | FEED_COMMENT 0.409, FEED_UPLOADED 0.217, DAILY_QUESTION_UPLOADED 0.158, FEED_TAGGED 0.124, FEED_COMMENT_REPLIED 0.093 |
| 검사 | 작성자 태그 없는 피드 0, `(feed_id, order_index)` 중복 0, FK 고아 0, 팀 밖 태그·반응·댓글 0, 데일리 key 형식 오류 0, 알림 팀·피드·actor 불일치 0 |
| `targets.json` | 약 2.1MB (팀 2,003개, 장소 항목 약 30,000개, 댓글 많은 피드 995건, 깊은 커서 9개) |
| DB 크기 | 4,784MB (`pg_database_size`. notification 약 2.8GB, feed_comment 약 0.7GB, feed 약 0.4GB) |

FEED_COMMENT가 많은 이유: 고른 피드의 작성자가 알림을 받는 회원 자신이면 FEED_COMMENT로 바꾸기 때문이다. 팀이 작아 이런 경우가 약 1/4이다.

### 7. 초기화

```bash
# 앱을 먼저 끈다 (Ctrl+C 또는 kill)
docker compose -f loadtest/docker-compose.yml down -v
docker compose -f loadtest/docker-compose.yml up -d --wait
```

`down -v`는 named volume `tikitak-loadtest-pgdata`와 Prometheus·Grafana 볼륨(`tikitak-loadtest-prometheus`, `tikitak-loadtest-grafana`)까지 지운다. 지난 측정의 Prometheus 시계열도 사라지므로, 비교에 쓸 그래프는 먼저 9-3대로 스크린샷을 남긴다.
다시 `up -d` 하면 빈 DB가 뜨고, 앱을 기동하면 Flyway가 V1~V25를 처음부터 적용한다(4번부터 다시).

### 8. 측정 (#217)

명령은 저장소 루트에서 Git Bash로 실행한다. 앱이 떠 있어야 한다(4·5번). k6는 v2.2.0에서 확인했다.

#### 8-0. 규칙: 앱 기동 후 매 측정 전 2분 웜업

**측정할 때마다 그 직전에 mixed 비중으로 2분 웜업을 한다.** `run-measure.sh`가 기본으로 한다.

- 이유: 앱 기동이나 시드 직후 첫 측정은 Postgres 버퍼 캐시와 JVM(JIT, 커넥션, Hibernate 메타데이터)이 비어 있어, 같은 API가 이후보다 수십 배 느렸다. 2026-11-24 스모크에서 solo `map_pins`가 첫 실행 P95 12,489ms, 캐시가 찬 뒤 960ms였고, solo `feed_by_place`는 1,933ms → 156ms, `best_attendance`는 4,539ms → 70ms였다. 웜업 없이 재면 실행 순서가 결과를 정한다.
- 무엇을: mixed와 같은 요청 비중으로 100 RPS × 2분(`PREWARM_RATE`, `PREWARM_DURATION`). solo·ramp 측정 전에도 mixed 비중으로 한다(한 API만 데우면 다른 API의 캐시 상태가 측정마다 달라진다).
- 언제 끝나나: 웜업 k6가 끝난 뒤, 앱에 남은 요청(처리 중 HTTP 요청, Hikari active·pending)이 3초 연속 0이 될 때까지 기다린 다음 `pg_stat_statements` reset과 Hikari 샘플러를 시작한다. 그래서 웜업 요청은 `-pgss.*`, `-hikari.csv`, 본 측정 지연에 섞이지 않는다. `PREWARM_DRAIN_TIMEOUT`(기본 180초) 안에 비지 않으면 측정을 중단한다.
- 콜드 상태에서 100 RPS 웜업은 앱이 감당하지 못해 지연이 10초를 넘고 `dropped_iterations`가 생긴다(20초 웜업 스모크에서 약 1,200~1,500건). 데우는 것이 목적이라 정상이다. 웜업 결과(`<RUN_ID>-prewarm-k6.log`, `-prewarm-k6-summary.json`)는 판정에 쓰지 않는다.
- 끄기: `PREWARM_DURATION=0`(또는 `off`). 같은 앱 기동에서 방금 웜업과 측정을 했고 바로 이어 재는 경우에만 끈다. 끄면 결과 문서에 적는다.
- mixed 모드 안의 1분 웜업(20 RPS, `scenario:warmup`)은 이 규칙과 별개로 그대로 있다. mixed의 `pg_stat_statements`에는 이 1분이 들어간다(8-5).

#### 8-1. 준비

```bash
# 6-2의 targets.json이 있어야 한다. 그다음 댓글 요청 대상을 뽑는다(약 1초, 약 380KB)
bash loadtest/scripts/extract-comment-feeds.sh
```

`comment-feeds.json`은 팀마다 피드 목록 첫 페이지에 보이는 최신 피드 20개의 ID다. `targets.json`에는 댓글 많은 피드만 있어서 따로 뽑는다.
k6 `setup()`에서 API로 가져오지 않는 이유는, solo 측정의 `pg_stat_statements`에 피드 목록 쿼리가 섞이지 않게 하려는 것이다.

#### 8-2. 요청 구성 (`k6/read-api-baseline.js`)

| `api` 태그 | 요청 | 비중 | 대상 고르기 |
|---|---|---|---|
| `map_pins` | `GET /api/v1/teams/{teamId}/map/pins` | 20% | 팀 |
| `feed_by_place` | `GET /api/v1/teams/{teamId}/feeds?placeId=<externalPlaceId>` | 20% | 팀 → 그 팀 장소 중 피드 수에 비례해 하나(큰 팀은 상위 100개 장소 안에서) |
| `feed_first` | `GET /api/v1/teams/{teamId}/feeds` | 15% | 팀 |
| `feed_deep` | `GET /api/v1/teams/{teamId}/feeds?cursor=<deepCursor>` | 5% | `deepCursors` 9개 중 하나(큰 팀만 있다) |
| `best_attendance` | `GET /api/v1/teams/{teamId}/home/best-attendance` | 5% | 팀 |
| `comments` | `GET /api/v1/teams/{teamId}/feeds/{feedId}/comments` | 10% | 20%는 댓글 많은 피드(500~2,000개, `commentHeavyFeeds`), 80%는 팀 → 그 팀 최신 피드 20개 중 하나 |
| `notifications` | `GET /api/v1/me/notifications` | 10% | 팀 → 회원. `teamId` 없이 회원 전체 알림 |
| `unread_count` | `GET /api/v1/me/notifications/unread-count` | 15% | 팀 → 회원. `teamId` 없이 |

- "팀"은 30% 확률로 큰 팀 3개 중 하나, 70%는 일반 팀 2,000개 중 하나다. 회원은 고른 팀의 멤버 중 무작위다. 댓글 많은 피드와 깊은 커서는 그 피드의 팀 멤버 중에서 고른다(댓글 많은 피드는 약 30%가 큰 팀이다).
- `size`, `type` 등 나머지 쿼리 파라미터는 보내지 않는다(서버 기본값: 페이지 20건).
- 댓글 많은 피드를 20%로 둔 이유: 실제 비율(피드의 0.1%)로 두면 측정 5분 동안 몇 건만 나와 그 경로의 지연이 보이지 않는다. 반대로 절반 이상이면 댓글 API 지연이 이 피드들로만 정해진다. 두 경우가 모두 P95에 드러나도록 고른 값이다.
- 요청마다 `tags: {api, name}`을 붙인다(`name`은 경로 템플릿이라 URL별 시계열이 늘지 않는다). ramp에서는 `stage` 태그도 붙는다.
- check: 상태 200, 응답 `success == true`, `feed_by_place`는 `data.items`가 1건 이상. 200이 아니면 응답 앞부분을 경고 로그로 남긴다(`LOG_ERRORS=0`이면 끈다).

**토큰**: `setup()`이 `JWT_SECRET`으로 `targets.json`의 모든 회원(8,557명)에게 HS256 access token을 서명한다. 형식은 `TokenService.createToken`과 같다.
header `{"alg":"HS256","typ":"JWT"}`, payload `{"sub":"<memberId>","type":"access","iat":..,"exp":..,"jti":"<uuid>"}`(만료 24시간), base64url 패딩 없음, 서명은 `crypto.hmac('sha256', secret, data, 'base64rawurl')`이다(k6 v2.2.0에서 `base64rawurl` 출력이 Java `Base64.getUrlEncoder().withoutPadding()`과 같은 것을 확인했다).
앱은 서명을 문자열로 비교하므로 인코딩이 다르면 401(AUTH009)이 난다. k6 시작부터 첫 요청까지(init과 서명 포함) 약 1.5초 걸렸다(2026-11-24).

#### 8-3. 모드

| 모드 | 실행기 | 기본값 | threshold |
|---|---|---|---|
| `mixed`(기본) | `warmup`: `constant-arrival-rate` 20 RPS 1분 → `measure`: `constant-arrival-rate` 100 RPS 5분(`startTime` = 웜업 시간). `preAllocatedVUs` 50, `maxVUs` 300 | `WARMUP=1m`, `DURATION=5m`, `RATE=100` | API별 `http_req_duration{scenario:measure,api:x}` `p(95)<300`, `http_req_failed{...}` `rate<0.01`, `checks{...}` `rate>0.99`, `dropped_iterations{scenario:measure}` `count==0`. 웜업은 `scenario:warmup`이라 판정에 섞이지 않는다 |
| `ramp` | `ramping-arrival-rate`. 단계마다 2분 유지, 단계 사이 10초는 선형 전환 | `RAMP_RATES=25,50,100,200,400`, `STAGE_DURATION=2m`, `TRANSITION=10s` | 단계별 `http_req_duration{stage:r025}` 등. 판정용이 아니라 요약에 단계별 P95를 남기려고 건다 |
| `solo` | `constant-arrival-rate` 한 API만 | `API=<태그>`, `RATE=20`, `DURATION=1m` | 그 API의 P95·실패율·check, `dropped_iterations` |

- ramp의 `stage` 태그는 시나리오 시작 기준 경과 시간으로 정한다. 유지 구간은 `r025`, `r050`, `r100`, `r200`, `r400`, 전환 구간은 `transition`이다. 전환 구간 요청은 단계별 지표에서 빠진다.
- ramp 전체 길이는 2분 × 5 + 10초 × 4 = 10분 40초다(그 뒤 gracefulStop 최대 30초).
- 공통 env: `BASE_URL`(API, 기본 `http://localhost:8080`), `MANAGEMENT_URL`(actuator, 기본 `http://localhost:8081`. setup의 health 확인), `MAX_VUS`(기본 300), `JWT_SECRET`(필수), `RUN_ID`, `SUMMARY_DIR`(기본 `loadtest/results/raw`).
- mixed 웜업 시나리오 env: `WARMUP`(기본 1m), `WARMUP_RATE`(기본 20), `WARMUP_ONLY=1`(웜업 시나리오만 돌리고 판정하지 않는다. `run-measure.sh`의 측정 전 웜업이 쓴다).
- 짧게 확인할 때는 `WARMUP=10s DURATION=30s`, `DURATION=10s`, `RAMP_RATES=5,10 STAGE_DURATION=10s TRANSITION=5s`처럼 줄이고, 측정 전 웜업도 `PREWARM_DURATION=20s`처럼 줄인다.

#### 8-4. 실행

```bash
bash loadtest/scripts/run-measure.sh mixed
bash loadtest/scripts/run-measure.sh ramp
bash loadtest/scripts/run-measure.sh solo map_pins     # API 태그 하나
PREWARM_DURATION=20s DURATION=10s bash loadtest/scripts/run-measure.sh solo comments   # 짧은 동작 확인용
```

`run-measure.sh`는 다음을 차례로 한다. `JWT_SECRET`이 env에 없으면 `loadtest/.env`에서 읽는다.

1. 앱 `$MANAGEMENT_URL/actuator/health`(기본 8081)와 입력 JSON 확인
2. 측정 전 웜업(8-0): mixed 비중 k6(`WARMUP_ONLY=1`)를 돌리고, 앱에 남은 요청이 끝날 때까지 기다린다
3. `pg_stat_statements_reset()`
4. Hikari 샘플러를 백그라운드로 시작(`$MANAGEMENT_URL/actuator/prometheus`)
5. `k6 run --quiet loadtest/k6/read-api-baseline.js` (ramp는 `--out csv=...-k6-metrics.csv.gz`를 더한다)
6. 샘플러 종료, `pg_stat_statements` 덤프
7. Hikari active/pending 최대값(ramp는 단계별로도) 출력

2026-11-24 짧은 확인(`PREWARM_DURATION=20s DURATION=10s ... solo map_pins`)의 실제 순서(파일 시각과 샘플 시각): 웜업 k6 종료 20:09:19 → 남은 요청 대기 → reset·샘플러 첫 샘플 20:09:35(active 0, pending 0) → k6 setup 끝 20:09:36 → k6 종료 20:09:49 → 덤프 20:09:51.
대기를 넣기 전에는 웜업 k6가 끝난 뒤에도 앱에 웜업 요청이 남아, 샘플러 첫 샘플이 active 10·pending 190이었다.

종료 코드는 k6의 것이다(threshold를 넘으면 99). threshold를 넘어도 덤프까지 한다.

결과 파일은 `loadtest/results/raw/<RUN_ID>-<mode>[-api]-*`로 남는다(`RUN_ID` = 호스트 로컬 시각 `yyyyMMdd-HHmmss`).

| 파일 | 내용 |
|---|---|
| `-prewarm-k6.log` / `-prewarm-k6-summary.json` | 측정 전 웜업 결과(`<RUN_ID>-prewarm-*`). 판정에 쓰지 않는다 |
| `-k6-summary.json` | k6 `handleSummary` 결과. `meta`(모드, 시나리오, 비중, setup 끝 시각, ramp 일정)와 모든 지표(P95·P99 포함) |
| `-k6.log` | k6 stdout. 끝에 API별(ramp는 단계별도) count·avg·P95·P99·max·실패율·check 표 |
| `-hikari.csv` | `epoch_ms,time,active,pending,idle,max`. 1초 간격 |
| `-pgss.csv` / `-pgss.txt` | `total_exec_time` 상위 50개 쿼리의 calls·rows·총/평균/최대 시간·공유 버퍼 hit/read·쿼리 전문(csv) / 합계와 표(txt) |
| `-stages.csv` | ramp만. 단계별 구간의 절대 시각(epoch ms) |
| `-k6-metrics.csv.gz` | ramp만. 요청 단위 원시 지표(단계별 `dropped_iterations` 시점 확인용) |

스크립트를 따로 쓸 수도 있다.

```bash
bash loadtest/scripts/pgss.sh reset
bash loadtest/scripts/pgss.sh dump loadtest/results/raw/<접두사> [N]       # 상위 N개(기본 50)
bash loadtest/scripts/hikari-sampler.sh loadtest/results/raw/<파일>.csv &  # 종료: kill <pid>
JWT_SECRET=... MODE=solo API=unread_count k6 run loadtest/k6/read-api-baseline.js
```

#### 8-5. 결과를 읽을 때

- **요청당 쿼리 수**(solo): `-pgss.csv`의 각 쿼리 `calls` ÷ k6 요청 수(`-k6.log` 표의 count). setup은 `/actuator/health`만 부르고 DB를 쓰는 API를 부르지 않는다. 앱 기동 직후에는 JDBC 메타데이터 조회(`SELECT c.oid, a.attnum, ...`)가 몇 번 섞일 수 있다.
- **mixed의 `pg_stat_statements`**는 웜업 시작 전에 초기화하므로 웜업 1분의 쿼리도 포함한다. P95 같은 지연 판정은 k6의 `scenario:measure` 값으로 한다.
- **캐시 상태**: 앱 기동이나 시드 직후 첫 실행은 Postgres 버퍼 캐시가 비어 있어, 같은 API가 이후 실행보다 몇 배~수십 배 느렸다(2026-11-24 스모크). 그래서 8-0의 측정 전 웜업을 한다. solo를 여러 개 이어서 돌릴 때도 실행 순서를 결과 문서에 적는다.
- **샘플러 빈 줄**: actuator를 8081로 분리한 뒤로는 API(8080) 스레드가 밀려도 샘플러가 응답을 받았다(2026-11-24 짧은 확인에서 pending 190 안팎인 구간에도 빈 샘플 0). 다만 호스트 CPU가 포화되면 여전히 2초 안에 못 받을 수 있다. 래퍼가 빈 샘플 수를 출력하고, 빈 구간의 pending은 알 수 없으므로 결과 문서에 그 사실을 같이 적는다.
- **k6도 같은 호스트의 CPU를 쓴다.** 요청 하나를 만들 때 무거운 일을 하면(예: SharedArray 전체를 훑는 것) 그 시간이 `http_req_blocked`·`http_req_connecting`과 지연에 섞인다. 의심되면 요약의 `iteration_duration`과 `http_req_duration`의 평균을 비교한다(정상이면 거의 같다).

### 9. 모니터링 (Prometheus·Grafana)

compose에 같이 뜬다(1번). 이 PC에서만 열린다(`127.0.0.1`).

| 무엇 | 주소 | 비고 |
|---|---|---|
| Grafana | http://localhost:53000 | 로그인 없이 보기 가능(익명 Viewer). 편집은 `admin` / `admin`(로컬 전용) |
| 대시보드 | http://localhost:53000/d/tikitak-loadtest-backend | Grafana 첫 화면(홈)도 이 대시보드다. 폴더 `loadtest` |
| Prometheus | http://localhost:59090 | 수집 대상: http://localhost:59090/targets (job `tikitak-loadtest`가 UP이어야 한다) |

- Prometheus는 컨테이너 안에서 `host.docker.internal:8081/actuator/prometheus`를 1초마다 수집한다. `extra_hosts: host.docker.internal:host-gateway`를 명시했다(2026-11-24 Windows Docker Desktop 29.9.2에서 UP 확인, 수집 1회 약 50ms. Linux Docker Engine에서는 확인하지 않았다).
- Grafana 데이터소스(`Prometheus (loadtest)`, uid `tikitak-loadtest-prometheus`)와 대시보드는 `grafana/provisioning/`이 자동으로 만든다. 화면에서 고친 대시보드는 저장되지 않는다. 바꾸려면 `grafana/dashboards/tikitak-backend-dashboard.json`을 고친다(30초마다 다시 읽는다).
- 데이터소스의 `timeInterval`을 1초로 둬서 패널의 `$__rate_interval`은 짧은 시간 범위에서 4초다(범위를 넓히면 `max(4×1s, $__interval + 1s)`로 커진다). 짧은 구간의 변화가 보이는 대신 그래프가 출렁인다.
- 측정 전 웜업(8-0)도 그래프에 나온다. 본 측정 구간은 9-2대로 고른다.

#### 9-1. 대시보드

운영용 대시보드(Grafana Cloud)를 로컬용으로 바꾼 복사본이다. 바꾼 것은 제목·uid, `ds` 변수 기본값(로컬 데이터소스), `Environment`(`$job`) 변수 기본값(`tikitak-loadtest`, Prometheus job 이름), 기본 시간 범위(최근 30분), 새로고침(5초), 태그(`loadtest` 추가)뿐이고 패널·쿼리는 그대로다.
패널: Overview(RPS, P95, P99, 5xx 비율, DB 커넥션 사용률, 가동 시간), Traffic & Latency(RPS·지연 추이, URI별 RPS·P95, 에러), Database(HikariCP: active·idle·max, pending, 커넥션 획득·사용 시간), JVM & System(CPU, 힙, 스레드·로그 에러).
2026-11-24 짧은 확인에서 Grafana HTTP API(`/api/ds/query`)로 RPS, P95, Latency, RPS by URI, DB Connections, Connections, Pending threads 패널 쿼리를 실행해 모두 값이 나왔다.

#### 9-2. 측정 시간대 고르기

본 측정 구간만 본다(앞의 웜업은 뺀다). 구간은 `-hikari.csv`의 첫 줄과 마지막 줄 시각이 정확하다(reset 직후 ~ k6 종료).

```bash
# 측정 구간 앞뒤 30초를 붙인 대시보드 URL
awk -F, 'NR==2{f=$1} END{print "http://localhost:53000/d/tikitak-loadtest-backend?from=" f-30000 "&to=" $1+30000 "&var-job=tikitak-loadtest"}' \
  loadtest/results/raw/<RUN_ID>-<mode>[-api]-hikari.csv
```

화면에서 고를 때는 오른쪽 위 시간 선택에 절대 시간(KST, 대시보드 타임존 `Asia/Seoul`)을 넣거나, 그래프를 드래그해 구간을 좁힌다. 측정 중 실시간으로 볼 때는 "Last 15 minutes"와 5초 새로고침이면 된다.
Overview의 숫자 패널은 고른 구간의 **마지막 값**이다(평균·최대가 아니다). 구간 전체를 비교할 때는 아래 시계열 패널을 본다. 판정 수치는 대시보드가 아니라 k6 요약(P95·P99)과 `-hikari.csv`(최대값)를 쓴다.

#### 9-3. 개선 전후 비교용 스크린샷

Prometheus 데이터는 `down -v`를 하거나 30일 보존 기간이 지나면 사라진다. 비교에 쓸 그래프는 측정 직후 남긴다.

1. 9-2의 URL로 측정 구간을 연다. URL에 `from`·`to`(epoch ms)가 들어 있어 같은 구간을 다시 열 수 있다. 이 URL을 결과 문서에 같이 적는다.
2. 비교할 패널(보통 "Latency (avg / P95 / P99)", "P95 by URI", "Connections", "Pending threads")을 패널 메뉴 → View(단축키 `v`)로 크게 연다.
3. OS 화면 캡처(Windows `Win+Shift+S`)로 저장한다. 이 Grafana에는 이미지 렌더러 플러그인을 설치하지 않았으므로 Grafana의 이미지 내보내기는 쓰지 않는다.
4. 파일 이름은 `<RUN_ID>-<mode>[-api]-<패널>.png`로 하고 결과 문서와 같은 `loadtest/results/` 아래에 둔다(`results/raw/`는 커밋하지 않는다).
5. 개선 후에도 같은 모드·같은 시간 길이·같은 패널로 찍어 나란히 둔다. 세로축 범위가 자동이라 두 그림의 눈금이 다를 수 있으니 숫자를 같이 적는다.
