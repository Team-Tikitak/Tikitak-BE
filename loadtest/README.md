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

### 7. 초기화

```bash
# 앱을 먼저 끈다 (Ctrl+C 또는 kill)
docker compose -f loadtest/docker-compose.yml down -v
docker compose -f loadtest/docker-compose.yml up -d --wait
```

`down -v`는 named volume `tikitak-loadtest-pgdata`까지 지운다. 다시 `up -d` 하면 빈 DB가 뜨고, 앱을 기동하면 Flyway가 V1~V25를 처음부터 적용한다(4번부터 다시).
