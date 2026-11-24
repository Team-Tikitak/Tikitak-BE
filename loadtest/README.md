# loadtest — 조회 API 부하 측정 환경

로컬 Docker Postgres에 대량 시드를 넣고, 같은 조건에서 조회 API를 측정하기 위한 자산이다.
#217(피드·알림 조회 API 베이스라인)에서 만들었고, #215도 같은 환경과 공통 시드를 쓴다.

**dev Supabase나 prod DB에 접속하거나 시드를 넣지 않는다.** 이 디렉터리의 모든 것은 로컬 컨테이너 전용이다.

## 구성

```
loadtest/
├── docker-compose.yml        # Postgres 17.6 + Redis (프로젝트 이름 tikitak-loadtest)
├── initdb/                   # 볼륨이 비어 있을 때 한 번 실행 (pg_stat_statements 확장 생성)
├── .env.example              # 앱 실행용 env 템플릿 (가짜 R2·Kakao·OAuth 값)
├── .env                      # 위 파일을 복사해 만든다. 커밋하지 않는다
├── sql/
│   ├── 00_common.sql         # 공통 시드: member, team, team_member (#217, #215 공유)
│   ├── 10_feed.sql           # 피드 시드: place, feed, feed_image, feed_tag, feed_reaction, feed_comment (#217)
│   ├── 20_notification.sql   # 알림 시드: notification (#217)
│   ├── 30_targets.sql        # k6 입력 JSON 추출 (읽기 전용, #217)
│   └── NN_<기능>.sql          # 기능별 시드. 00_common 다음에 번호 순서대로 실행한다
├── k6/data/                  # 시드에서 뽑은 k6 입력. 커밋하지 않는다
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
| 호스트 포트 | Postgres **55432**, Redis **56379** (로컬의 5432/6379와 겹치지 않게) | 〃 |
| 컨테이너·볼륨 | `tikitak-loadtest-postgres`, `tikitak-loadtest-redis`, 볼륨 `tikitak-loadtest-pgdata` | 〃 |
| 앱 | `./gradlew bootJar` 결과 jar를 `java -Duser.timezone=Asia/Seoul -jar`로 실행. 프로필 `dev`. devtools 안 씀 | 아래 절차 |
| Hikari 풀 | `SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=10` | `.env.example` |

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

### 1. DB·Redis 기동

```bash
docker compose -f loadtest/docker-compose.yml up -d --wait
```

`--wait`는 두 컨테이너가 healthy가 될 때까지 기다린다. 볼륨이 비어 있으면 `initdb/01_pg_stat_statements.sql`이 실행돼 `pg_stat_statements` 확장이 만들어진다.

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
Windows에서 `.env`를 편집기로 열어 저장했다면 줄 끝이 CRLF가 되지 않았는지 확인한다(Git Bash의 `source`는 값 끝에 `\r`을 붙인다).

### 4. 앱 실행 (Flyway가 V1~V25를 적용한다)

JDK 21 이상이 필요하다. 앱은 포트 8080을 쓴다.

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
curl -s localhost:8080/actuator/health
# {"groups":["liveness","readiness"],"status":"UP"}

docker compose -f loadtest/docker-compose.yml exec -T postgres psql -U tikitak -d tikitak -Atc \
  "SELECT count(*) FILTER (WHERE success), max(version::int) FROM flyway_schema_history"
# 25|25

curl -s localhost:8080/actuator/prometheus | grep -E '^hikaricp_connections_(max|active|pending)'
# hikaricp_connections_max{...} 10.0
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

`down -v`는 named volume `tikitak-loadtest-pgdata`까지 지운다. 다시 `up -d` 하면 빈 DB가 뜨고, 앱을 기동하면 Flyway가 V1~V25를 처음부터 적용한다(4번부터 다시).
