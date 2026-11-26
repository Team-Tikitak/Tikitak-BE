# 지도 핀 조회 인덱스 개선 측정 (#220) — 2026-11-26

`GET /api/v1/teams/{teamId}/map/pins`(`FeedRepository.findMapPinsByTeamId`)를 V26 인덱스 `idx_feed_team_place_latest` + 후보 L(GROUP BY 개수 + 장소별 `CROSS JOIN LATERAL … LIMIT 1`)로 바꾸고, 같은 시드 위에서 개선 전후를 잰 결과다.
계약: `docs/contracts/2026-11-26-map-pin-latest-index.md`. 결정 기록: `docs/decisions/2026-11-26-map-pin-latest-index.md`. 베이스라인: [2026-11-24-baseline.md](2026-11-24-baseline.md). 쿼리 파일: `loadtest/sql/explain-map-pins.sql`.

원시 파일은 `loadtest/results/raw/`에 있다(커밋하지 않는다). 표마다 원시 파일 이름을 적었다. 계산하거나 추정한 값은 그렇게 표시했다.

## 요약

- **solo map_pins(20 RPS, 1분): 합격.** P95 4,444.0ms → **81.4ms**, dropped 18 → **0**(`20261126-1554-{before,after}-solo-solo-map_pins-k6-summary.json`).
- **mixed(100 RPS, 5분): 불합격.** map_pins P95 12,556.9ms → **7,379.7ms**, 다른 7개 API도 P95 7.2~7.8초다. dropped 19,308 → 12,506, 처리량 35.6 → 58.3 RPS(계산).
- **지도 핀 쿼리의 mixed DB 시간 비중: 37.5% → 7.3%**(평균 307.3ms → 34.4ms). mixed가 여전히 불합격인 것은 안 읽은 알림 수(32.7%)와 피드 수 `count(distinct)`(20.8%) 같은 다른 쿼리가 만든 풀 대기 때문이다. #221~#225를 고친 뒤 mixed로 다시 판정한다(2026-11-26 사람 결정).
- **EXPLAIN(부하 없음, 2회차)**: 큰 팀 1은 129.5ms → **15.8ms**(버퍼 20,464 → 2,179, 힙 19,128블록 → 힙 접근 0). **일반 팀 4는 1.19ms → 2.66ms로 느려졌다.** 플래너가 장소 그룹 수를 186으로 추정(실제 22)해 place 5,000행 seq scan + hash join을 골랐다. #220에서는 고치지 않는다(사람 결정).
- **응답은 같다**: 전체 2,003팀에서 개선 전 SQL과 L의 결과가 31,008행으로 같고, EXCEPT ALL 양방향 0행이다.
- **V26 생성 시간**: feed 1,000,287행에서 1.812초(Flyway `execution time`).

## 1. 환경과 시드

환경(호스트, Docker, Postgres 설정·컨테이너 제한, JVM, Hikari 10, k6 v2.2.0)은 베이스라인 1절과 같은 PC·같은 compose다. 이번에 다시 확인하지 않은 값은 옮기지 않았다. 이 폴더의 compose 값은 `LOADTEST_PROJECT=tikitak-loadtest-220`, Postgres 55434, 앱 8084·8085다(계약 측정 절차).

jar 두 개(`raw/jar/before.jar` = origin/develop 2579dba, `raw/jar/after.jar` = 이 브랜치의 작업 트리)를 썼다. after.jar는 커밋 1(b1d4dca, 15:44:17)보다 먼저 15:26:20에 빌드했다. 빌드 시점의 소스가 커밋 1과 같은 것은 이렇게 확인했다: 바뀐 소스의 마지막 수정 시각(V26 15:09:56, `FeedRepository.java` 15:10:02)이 빌드보다 앞서고, 커밋 1 뒤 `git diff HEAD -- src/`가 비어 있다. jar 안의 V26 파일은 커밋 1의 V26과 `diff`로 같았다(세션 확인, 원시 파일 없음). 앱이 보낸 문장이 L 형태인 것도 개선 후 `-pgss.csv`에서 확인했다(5절). 앱 로그의 `Starting TikitakApplication ... (…\raw\jar\before.jar …)`로 어느 jar인지 확인했다(`raw/app-seed.log`, `raw/20261126-1554-{before,after}-app.log`).

### 시드

빈 DB에 before.jar로 Flyway를 적용(`raw/app-seed.log`: `Successfully applied 25 migrations ... now at version v25`, 15:13:32)한 뒤 앱을 끄고 README 6~6-2를 실행했다.

| 단계 | 시각 | 근거 |
|---|---|---|
| `00_common.sql` | 15:13:57 ~ 15:14:01 | 시드 실행 세션 출력(작업 트리 밖 임시 파일, `raw/`에는 없음). `raw/seed-00_common.log` 수정 시각 15:14:01 |
| `10_feed.sql` | 15:14:01 ~ 15:19:17 | 〃, `raw/seed-10_feed.log` 수정 시각 15:19:17 |
| `20_notification.sql` | 15:19:17 ~ 15:24:20 | 〃, `raw/seed-20_notification.log` 수정 시각 15:24:20 |
| `30_targets.sql`, `extract-comment-feeds.sh` | ~ 15:24:30 | 세션 출력에는 끝 시각(`[seed] done 15:24:30`)만 있다. `k6/data/targets.json` 수정 시각 15:24:28, `comment-feeds.json` 15:24:30 |

건수(베이스라인 2절과 비교):

| 항목 | 이번 | 베이스라인(2026-11-24) | 근거 |
|---|---|---|---|
| feed | 1,000,287 | 1,000,287 | `raw/seed-10_feed.log` "건수" |
| 데일리 답변 | **149,369** | 148,993 | `raw/seed-10_feed.log` "데일리 답변 검사"(bad_* 모두 0) |
| 큰 팀 1의 장소 있는 활성 피드 / 장소 수 | 60,147 / 287 | 60,184행(베이스라인 7-1 EXPLAIN) | `raw/seed-10_feed.log` "큰 팀별 피드"(`with_place`, `distinct_places`). EXPLAIN 파일 (0)절에도 같은 값 |
| 큰 팀 1 최다 장소 피드 | 8,965 | 8,977 | `raw/seed-10_feed.log` |
| notification | 10,003,910 | 10,003,910 | `raw/seed-20_notification.log` |

데일리 답변은 베이스라인보다 **376건 많다**. 시드 시각이 `LOCALTIMESTAMP` 기준이라 날짜 경계에 걸리는 피드가 실행 시각마다 바뀐다(베이스라인 9절). 큰 팀 1의 장소 있는 피드도 60,184 → 60,147로 조금 다르다. 데일리 답변 피드는 장소가 없으므로(`bad_with_place` 0) 같은 원인으로 추정한다(확인하지 않았다). 개선 전후 비교는 모두 이번 시드 하나에서 했다.

## 2. 절차

### 잠금 1: 쿼리 형태 선택 (15:25)

시드 직후 앱을 끈 상태에서 `BEGIN; V26 CREATE INDEX; explain-map-pins.sql ×2; ROLLBACK;`을 팀 1·팀 4로 실행했다. 이어서 같은 형태로 `-v compare=1` 전체 팀 비교를 실행했다. 명령은 `explain-map-pins.sql` 머리 주석의 "잠금 1"이다.

- 원시 파일: `raw/20261126-1524-lock1-explain-team1.txt`, `-team4.txt`, `-lock1-compare.txt`(파일 수정 시각 15:25:01, 15:25:02, 15:25:19).
- (0)절에서 `idx_feed_team_place_latest`의 `indcheckxmin`은 `f`였다. 같은 트랜잭션에서 만든 인덱스를 EXPLAIN이 쓸 수 있는 상태였고, 실제로 L·D 계획에 이 인덱스가 나온다.
- compare 파일 끝이 `ROLLBACK`이다. 잠금 2의 개선 전 EXPLAIN (0)절이 `(0 rows)`라 인덱스가 남지 않았다.
- 이 EXPLAIN은 시드와 별도의 `measure-lock.sh` 잠금 안에서 실행했다. 잠금 획득 15:24:56(대기 0초), 해제 15:25:20(세션 출력의 `[measure-lock]` 줄, 원시 파일 없음). 끝에서 `idx_feed_team_place_latest`가 남지 않은 것(`pg_indexes` 0건)을 확인했다.
- 여기서 L을 골랐다(4절). after.jar는 그 뒤에 만들었다(`raw/jar/after.jar` 수정 시각 15:26:20).

### 폐기한 실행: 잠금 2 첫 시도 (`20261126-1527-*`)

15:27:03에 잠금 2 스크립트를 처음 실행했는데 개선 전 solo만 재고 멈췄다. 결과는 판정에 쓰지 않는다.

- 오염: `raw/20261126-1527-before-solo-solo-map_pins-contamination.txt`가 `CONTAMINATION: POSSIBLE`이다. 구간 15:28:09~15:32:42, 호스트 샘플 123개 기준 외부 프로세스 CPU 평균 4.18(최대 6.37, 4.0 이상 73개). 주요 프로세스 `asdsvc` 평균 1.34. 시작 시 여유 메모리 388MB(끝 1,509MB).
- 그 상태의 solo 값: P95 30,186.7ms, dropped 325, 응답 876건 중 401(AUTH009) 1건(fail 0.1%)(`-k6-summary.json`. 401 본문은 `-k6.log`의 k6 경고 1줄). 401의 원인은 확인하지 않았다.
- 멈춘 이유: 스크립트가 `set -e`였고, `run-measure.sh`가 threshold 실패로 exit 99를 내자 그 자리에서 끝났다(실행 로그 끝이 `k6 exit code: 99` 뒤 바로 잠금 해제 15:32:53). 개선 전 mixed와 개선 후 측정은 실행되지 않았다. 다시 실행한 스크립트는 `run-measure.sh` 호출 뒤에 `|| log "run-measure exit=$? …"`가 있어 threshold 실패에도 다음 단계로 넘어간다(1554 실행 로그에 `run-measure exit=99` 줄이 세 번 있다. 개선 후 solo는 exit 0).

### 잠금 2: 개선 전 → 개선 후 (`20261126-1554-*`, 15:54:08 ~ 16:21:07)

측정 스크립트는 작업 트리 밖 임시 파일이라 커밋하지 않았다. 순서는 아래와 같다(실행 로그도 작업 트리 밖이다. 시각은 그 로그의 `[lock2 …]` 줄).

1. `loadtest/.env`를 읽어 before.jar 기동(15:54:37 health UP). Flyway는 `Schema "public" is up to date`(V25)였다(`raw/20261126-1554-before-app.log`).
2. 부하 없이 `explain-map-pins.sql` ×2를 팀 1·팀 4로 실행(15:54:40 끝) → `-before-explain-team{1,4}.txt`.
3. `run-measure.sh solo map_pins` → `-before-solo-*`. 이어서 `run-measure.sh mixed` → `-before-mixed-*`. 둘 다 측정 전 웜업 2분(mixed 비중 100 RPS)과 남은 요청 대기를 래퍼가 했다(README 8-0).
4. 앱 종료(16:07:25) → after.jar 기동(16:08:14 health UP). Flyway가 V26을 적용했다.
5. 부하 없이 EXPLAIN ×2(16:08:17 끝) → `-after-explain-team{1,4}.txt`. 이어서 `-v team_id=1 -v compare=1`로 전체 팀 비교 → `-after-compare.txt`.
6. `run-measure.sh solo map_pins` → `-after-solo-*`, `run-measure.sh mixed` → `-after-mixed-*`.
7. 앱 종료(16:21:06), Prometheus 데이터 디렉터리를 작업 트리 밖에 복사(16:21:07): `C:\Users\06har\Documents\tikitak-loadtest-220-prometheus-20261126-1554`. 패널 스크린샷은 찍지 않았다.

잠금 2의 EXPLAIN은 앱이 떠 있는 상태에서 했지만, 웜업·측정 부하를 걸기 전이라 요청은 없었다(잠금 1은 앱을 끈 상태). ramp는 재지 않았다(미측정). 측정 뒤 볼륨은 지우지 않았다(#224의 개선 전 상태로 쓴다).

## 3. V26 생성 시간

`raw/20261126-1554-after-app.log` 45~46행:

- `Migrating schema "public" to version "26 - add feed team place latest index"` 16:07:43.838
- `Successfully applied 1 migration to schema "public", now at version v26 (execution time 00:01.812s)` 16:07:45.761

feed 1,000,287행(장소 있는 활성 피드는 그중 약 60%, 베이스라인 2절 `place 있는 피드 비율 0.600`)에서 **1.812초**다. 인덱스 크기는 28MB(`-after-explain-team1.txt` (0)절). 생성 중 다른 쓰기는 없었다. 운영 feed 행 수와 운영 DB 성능에서의 생성 시간은 미확인이다.

## 4. EXPLAIN (ANALYZE, BUFFERS, SETTINGS)

모두 부하 없이 같은 파일을 두 번 넘긴 **2회차**(캐시가 찬 상태) 값이다. `EXECUTE`로 실행한 custom plan이다(generic plan은 이번에 재지 않았다). 단위는 ms.

### 4-1. 판정용: 개선 전(V25, 개선 전 SQL) vs 개선 후(V26, L)

| 대상 | 상태 | 원시 파일 | 실행 시간 | 버퍼(shared hit) | 읽은 행·계획 요지 |
|---|---|---|---|---|---|
| 큰 팀 1 | 개선 전 | `-1554-before-explain-team1.txt` (i) 2회차 | **129.5** (1회차 131.2) | 20,464 | `idx_feed_map_pin_lookup` bitmap → 힙 19,128블록에서 60,147행 → `place_id, created_at DESC, id DESC` 정렬(quicksort 3,886kB) → WindowAgg 2단 → 59,860행 버리고 287행 |
| 큰 팀 1 | 개선 후 | `-1554-after-explain-team1.txt` (ii) 2회차 | **15.8** (15.755, 1회차 14.8) | 2,179 | 개수: `idx_feed_map_pin_lookup` Index Only Scan 60,147행(heap fetch 0, 70버퍼) → GroupAggregate 287그룹. 최신 1건: `idx_feed_team_place_latest` Index Only Scan 287회 × 1행(heap fetch 0, 862버퍼). place Seq Scan 5,000행(99) + Hash Join, feed_image 287회(1,148) |
| 일반 팀 4 | 개선 전 | `-1554-before-explain-team4.txt` (i) 2회차 | **1.19** (1.193, 1회차 2.08) | 343 | bitmap → 힙 186블록에서 188행 → 정렬 → WindowAgg → 22행. 전체 추정 1행이라 place는 `place_pkey` Nested Loop 22회 |
| 일반 팀 4 | 개선 후 | `-1554-after-explain-team4.txt` (ii) 2회차 | **2.66** (2.655, 1회차 3.31) | 258 | 개수: Index Only Scan 188행(4버퍼) → GroupAggregate 추정 **186그룹, 실제 22**. 그래서 place Seq Scan 5,000행(99버퍼, 0.79ms) + Hash Join을 골랐다. 최신 1건 22회 × 1행(67버퍼) |

일반 팀 4는 버퍼는 줄었지만(343 → 258) 실행 시간은 1.19 → 2.66ms로 늘었다. 늘어난 시간의 대부분은 place 5,000행 seq scan과 hash 생성이다(Hash Join 끝 1.960ms 중 Seq Scan 0.793ms). 운영처럼 place가 크면 플래너가 seq scan 대신 Nested Loop을 고를 것으로 추정하지만 확인하지 않았다(5,000행 place에서만 봤다).

### 4-2. 참고: 같은 상태의 다른 후보

| 대상 | 상태 | 원시 파일 | 개선 전 SQL | L | D |
|---|---|---|---|---|---|
| 큰 팀 1 | 잠금 1 (트랜잭션 안 V26, `indcheckxmin` f) | `-1524-lock1-explain-team1.txt` 2회차 | 58.0 | **15.3** | 27.3 |
| 일반 팀 4 | 〃 | `-1524-lock1-explain-team4.txt` 2회차 | 1.32 | 3.26 | **1.52** |
| 큰 팀 1 | 잠금 2 개선 후 (V26) | `-1554-after-explain-team1.txt` 2회차 | 49.2 | **15.8** | 21.7 |
| 일반 팀 4 | 〃 | `-1554-after-explain-team4.txt` 2회차 | 1.09 | 2.66 | **2.62** |

- 잠금 1의 1회차(콜드)는 큰 팀 1에서 321.6 / 16.2 / 22.6, 일반 팀 4에서 50.5 / 5.03 / 2.46이었다. 개선 전 SQL만 1회차가 크게 느린 것은 그 트랜잭션에서 방금 만든 인덱스 페이지를 처음 읽었기 때문이다(read 699, `written` 346).
- **V26만 있으면 개선 전 SQL도 빨라진다**(129.5 → 49.2ms). 새 인덱스의 Index Only Scan이 bitmap heap scan과 정렬을 대신하기 때문이다. 그래도 60,147행 전체에 WindowAgg를 돌려 L보다 약 3배 느리다.
- D는 최신 1건을 `idx_feed_team_place_latest` 전체(60,147행)를 순서대로 읽고 Unique로 고른다(366버퍼). L은 장소마다 1행만 읽는다.
- L·D 모두 개수를 세는 GroupAggregate는 V18 `idx_feed_map_pin_lookup`(team_id, place_id)을 쓴다(위 두 파일의 계획). 더 작은 인덱스(70버퍼 vs 새 인덱스 366버퍼)라 플래너가 고른 것으로 본다.
- 형태 선택은 계약대로 **큰 팀 1의 실행 시간**으로 했다(L 15.3 vs D 27.3, 잠금 1). 일반 팀에서는 D가 빨랐다(1.52 vs 3.26). 이유는 결정 기록에 적었다.

## 5. 결과 비교 (전체 팀)

`explain-map-pins.sql` (iv)절. 팀마다 개선 전 SQL·L·D를 팀 id만 바꿔 실행해 임시 테이블에 넣고, 팀 id + 반환 컬럼 전체로 `EXCEPT ALL` 양방향 비교한다(order_index 0 이미지가 두 행인 경우의 중복까지 보려고 `EXCEPT`가 아니라 `EXCEPT ALL`).

| 실행 | 원시 파일 | teams | before_rows | cand_l_rows | cand_d_rows | before−L | L−before | before−D | D−before |
|---|---|---|---|---|---|---|---|---|---|
| 잠금 1 (트랜잭션 안 V26) | `-1524-lock1-compare.txt` | 2,003 | 31,008 | 31,008 | 31,008 | 0 | 0 | 0 | 0 |
| 잠금 2 개선 후 (V26) | `-1554-after-compare.txt` | 2,003 | 31,008 | 31,008 | 31,008 | 0 | 0 | 0 | 0 |

두 번 모두 4방향 0행이다. 앱이 실제로 L 형태의 SQL을 보냈는지는 개선 후 mixed·solo `-pgss.csv`의 문장(`WITH counts AS ( … CROSS JOIN LATERAL ( … LIMIT 1 ) latest …`)으로 확인했다. 시나리오별 비교는 커밋 1의 `MapPinQueryIntegrationTest`가 한다.

## 6. solo map_pins (20 RPS × 1분)

판정 기준: P95 ≤ 300ms, 실패율 < 1%, dropped 0. 측정 구간은 `pg_stat_statements` reset 직후부터 k6 종료까지(Hikari 샘플 첫 줄~마지막 줄)다.

| 항목 | 개선 전 | 개선 후 | 근거 |
|---|---|---|---|
| 구간 | 15:57:06 ~ 15:58:15 (Hikari 69샘플) | 16:10:53 ~ 16:11:57 (64샘플) | `-solo-solo-map_pins-hikari.csv` |
| 응답 수 | 1,183 | 1,201 | `-k6-summary.json` `http_req_duration{scenario:solo,api:map_pins}.count` |
| 처리량(계산 = 응답 수 ÷ 60초) | 19.7 RPS | 20.0 RPS | |
| P50 / P95 / P99 / max | 313.4 / **4,444.0** / 6,484.8 / 7,698.9 | 29.5 / **81.4** / 97.6 / 135.7 | 〃 |
| 실패율 / check | 0% / 100% | 0% / 100% | 〃 |
| dropped | **18** | **0** | `dropped_iterations{scenario:solo}` |
| Hikari active 최대 / pending 최대 | 10 / 55 (pending > 0 35/69샘플, 빈 샘플 0) | 1 / 0 (빈 샘플 0) | `-hikari.csv` |
| 지도 핀 쿼리 calls / 평균 / 최대 | 1,183 / 278.3 / 2,818.2 | 1,201 / 14.8 / 110.9 | `-pgss.csv` 1행 |
| 지도 핀 쿼리 호출당 버퍼 hit / read | 6,094 / 6.8 | 776 / 7.0 | `-pgss.csv` (`shared_blks_hit`·`shared_blks_read` ÷ calls, 계산) |
| 요청당 쿼리 수(계산 = SQL calls ÷ 응답 수, `BEGIN READ ONLY` 제외) | (5,915 − 1,183) ÷ 1,183 = 4.00 | (6,005 − 1,201) ÷ 1,201 = 4.00 | `-pgss.txt` 전체 calls, `-pgss.csv` |
| 판정 | 불합격 | **합격** | |

단위는 ms다. 쿼리 수는 그대로 4개다(member, team_member 권한, team, 지도 핀).
개선 전 P95(4,444.0ms)는 베이스라인 solo(6,301.3ms, 2026-11-24)와 다르다. 다른 날·다른 시드 실행이라 비교에는 이번 개선 전 값을 쓴다.

## 7. mixed (100 RPS 고정 도착률, 5분)

측정 전 웜업 2분(100 RPS) → 남은 요청 대기 → `pg_stat_statements` reset → k6 웜업 1분(20 RPS) → 측정 5분이다(README 8-0).
측정 5분 구간은 k6 `setupFinishedAt` + 60초부터 300초로 계산했다(`-k6-summary.json` `meta.setupFinishedAt`). 개선 전 16:01:56.8~16:06:56.8, 개선 후 16:15:41.7~16:20:41.7.
판정 기준: API별 P95 ≤ 300ms, 실패율 < 1%, dropped 0.

### 7-1. API별 (`-1554-{before,after}-mixed-mixed-k6-summary.json`, `scenario:measure`)

| API | 개선 전 응답 수 | 개선 전 처리량 | 개선 전 P50 / P95 / P99 | 개선 후 응답 수 | 개선 후 처리량 | 개선 후 P50 / P95 / P99 | 판정 |
|---|---|---|---|---|---|---|---|
| map_pins | 2,161 | 7.20 | 8,572.6 / **12,556.9** / 15,367.0 | 3,444 | 11.48 | 5,019.4 / **7,379.7** / 8,956.9 | 불합격 |
| feed_by_place | 2,168 | 7.23 | 8,519.6 / 12,560.8 / 15,184.0 | 3,517 | 11.72 | 5,186.7 / 7,508.5 / 9,191.0 | 불합격 |
| feed_first | 1,586 | 5.29 | 8,491.3 / 12,550.6 / 15,200.2 | 2,594 | 8.65 | 5,202.6 / 7,495.8 / 8,932.6 | 불합격 |
| feed_deep | 526 | 1.75 | 8,871.9 / 12,415.2 / 15,128.1 | 850 | 2.83 | 5,472.0 / 7,762.1 / 9,372.5 | 불합격 |
| best_attendance | 575 | 1.92 | 8,266.1 / 12,498.6 / 15,289.1 | 907 | 3.02 | 5,092.2 / 7,546.9 / 8,590.3 | 불합격 |
| comments | 1,056 | 3.52 | 8,236.8 / 12,075.6 / 15,241.0 | 1,754 | 5.85 | 4,999.9 / 7,296.0 / 8,845.4 | 불합격 |
| notifications | 1,034 | 3.45 | 8,194.8 / 12,048.2 / 15,326.2 | 1,746 | 5.82 | 4,972.4 / 7,213.7 / 8,948.8 | 불합격 |
| unread_count | 1,586 | 5.29 | 8,341.8 / 12,487.1 / 14,875.6 | 2,683 | 8.94 | 5,150.6 / 7,494.2 / 9,002.9 | 불합격 |
| **합계** | **10,692** | **35.6** | | **17,495** | **58.3** | | |
| dropped | **19,308** | | | **12,506** | | | |

단위는 ms, 처리량은 RPS(계산 = 응답 수 ÷ 300초)다. 실패율은 모든 API가 0%, check는 모두 100%다.
응답 수 + dropped는 개선 전 30,000, 개선 후 30,001이다(100 RPS × 300초 = 30,000. 개선 후 1건 차이는 k6 도착률 실행기의 경계에서 한 번 더 시작한 반복으로 추정한다. 확인하지 않았다).
두 실행 모두 측정 시나리오(`scenario=measure`)에서 k6가 `Insufficient VUs, reached 300 active VUs`를 냈다. 개선 전 16:02:05(계산한 측정 시작 약 8초 뒤), 개선 후 16:15:58(약 16초 뒤)다(`-mixed-mixed-k6.log` 1행).

### 7-2. Hikari (`-mixed-mixed-hikari.csv`, reset 직후 ~ k6 종료)

| 항목 | 개선 전 | 개선 후 |
|---|---|---|
| 구간 / 샘플 수 / 빈 샘플 | 16:00:53 ~ 16:07:05 / 372 / 0 | 16:14:38 ~ 16:20:46 / 369 / 0 |
| active 최대 / pending 최대 | 10 / 190 | 10 / 190 |
| pending > 0 샘플 | 307 / 372 | 303 / 369 |
| pending 평균(전체 샘플) | 154.0 | 152.5 |

개선 후에도 풀은 포화 상태다. 같은 포화 안에서 요청당 처리 시간이 줄어 처리량이 35.6 → 58.3 RPS로 늘고, 대기열에 머무는 시간이 줄어 P95가 약 12.5초 → 7.4초로 내려갔다.

### 7-3. `pg_stat_statements` (`-mixed-mixed-pgss.txt`, `-pgss.csv`)

구간은 reset(개선 전 16:00:53, 개선 후 16:14:38)부터 덤프까지이며 k6 웜업 1분(20 RPS)을 포함한다(약 6분 10초).

| 항목 | 개선 전 | 개선 후 |
|---|---|---|
| 문장 수 / calls | 141 / 87,983 | 139 / 137,866 |
| 총 실행 시간(분모) | **1,979.7초** | **1,731.2초** |
| `shared_blks_read` | 712,711 | 1,171,836 |
| 상위 50개 합 / 50개 밖 | 1,939.2초 / 40.5초 | 1,680.2초 / 51.0초 |

**비중 = 그 문장의 `total_exec_time` ÷ 같은 덤프의 전체 총 실행 시간(위 분모).** 상위 문장(각 덤프의 상위 5개 + best-attendance 태그 집계):

| 쿼리 | 쓰는 API | 개선 전 총 시간 / 비중 / calls / 평균 | 개선 후 총 시간 / 비중 / calls / 평균 |
|---|---|---|---|
| **지도 핀** (전: `WITH ranked …`, 후: `WITH counts … CROSS JOIN LATERAL …`) | map_pins | 741.6초 / **37.5%** / 2,413 / 307.3ms | 126.5초 / **7.3%** / 3,681 / 34.4ms |
| `count(n1_0.id) … member_id=$1 and is_read=$2` | unread_count | 458.7초 / 23.2% / 1,767 / 259.6ms (read 422,062) | 566.4초 / **32.7%** / 2,868 / 197.5ms (read 684,568) |
| `count(distinct f1_0.id) from feed … team_id=$1` | feed_first, feed_deep | 261.6초 / 13.2% / 2,349 / 111.4ms | 359.3초 / **20.8%** / 3,676 / 97.7ms |
| 피드 목록 장소 필터 (`p1_0.external_place_id=$2 … fetch first`) | feed_by_place | 101.3초 / 5.1% / 2,415 / 41.9ms | 154.2초 / 8.9% / 3,757 / 41.0ms |
| 피드 목록 커서 (`created_at<$2 or …`) | feed_deep | 99.2초 / 5.0% / 578 / 171.7ms | 135.1초 / 7.8% / 910 / 148.5ms |
| 태그 집계 (`feed_tag … group by tm1_0.id`) | best_attendance | 47.9초 / 2.4% / 627 / 76.4ms | 71.0초 / 4.1% / 961 / 73.9ms |

계산 예: 개선 전 지도 핀 741,601.3ms ÷ 1,979,713.2ms = 37.46%, 개선 후 126,520.9ms ÷ 1,731,202.2ms = 7.31%.
지도 핀 쿼리의 비중과 평균은 크게 줄었다. 다른 쿼리는 처리량이 늘어 calls가 약 1.5배가 됐고, 그만큼 총 시간과 비중이 커졌다. 안 읽은 알림 수는 호출당 read가 239블록(684,568 ÷ 2,868, 계산)으로, 베이스라인 7-2의 I/O 문제가 그대로다.

### 7-4. 베이스라인 7-2 수요 계산 다시 하기 (추정)

베이스라인 7-2는 "부하 없는 EXPLAIN 2회차 × 100 RPS mixed 비중(큰 팀 30%)"으로 DB 시간 수요를 계산했다. 지도 핀 줄만 이번 개선 후 EXPLAIN(4-1, 큰 팀 1 L 15.755ms, 일반 팀 4 L 2.655ms)으로 바꾼다. 나머지 줄은 베이스라인(2026-11-24 시드의 EXPLAIN) 값을 그대로 둔다.

| API(RPS) | 계산 | DB 시간 수요(ms/초) |
|---|---|---|
| map_pins (20), 베이스라인 | 20 × (0.3 × 117.6 + 0.7 × 1.05) | 720 |
| map_pins (20), 개선 후 | 20 × (0.3 × 15.755 + 0.7 × 2.655) | **약 132** |
| 합계, 베이스라인 | | 약 2,260 |
| 합계, 개선 후 | 2,260 − 720 + 132 | **약 1,670 (CPU 약 1.7개)** |

이 값은 추정이다. 병렬 워커 CPU와 I/O 대기(안 읽은 알림 수)는 베이스라인과 같이 빠져 있다. 수요 추정은 CPU 2개 제한 아래로 내려왔지만, 실제 mixed는 여전히 pending 190으로 포화됐다(7-2). 빠진 I/O 대기와 부하 중 실행 시간 증가 때문으로 추정한다(확인하지 않았다).

## 8. 외부 부하 (`-contamination.txt`)

| 측정 | 구간 | 호스트 샘플 | 외부 프로세스 CPU 평균 / 최대 / 4.0 이상 | 측정 앱 평균 | 여유 메모리 시작 / 끝 | 다른 컨테이너 | 판정 |
|---|---|---|---|---|---|---|---|
| 개선 전 solo | 15:54:42 ~ 15:58:22 | 102 | 0.95 / 1.61 / 0 | 0.69 | 3,643 / 3,584MB | 없음(221샘플 평균 0.0%) | NONE |
| 개선 전 mixed | 15:58:28 ~ 16:07:14 | 255 | 1.26 / 4.38 / 1 | 0.51 | 3,524 / 3,277MB | 없음(527샘플 0.0%) | NONE |
| 개선 후 solo | 16:08:29 ~ 16:12:04 | 99 | 0.95 / 2.01 / 0 | 1.03 | 3,421 / 3,487MB | 없음(218샘플 0.0%) | NONE |
| 개선 후 mixed | 16:12:13 ~ 16:20:55 | 252 | 1.09 / 3.23 / 0 | 0.64 | 3,578 / 3,695MB | 없음(523샘플 0.0%) | NONE |
| (폐기) 1527 개선 전 solo | 15:28:09 ~ 15:32:42 | 123 | **4.18** / 6.37 / 73 | 0.85 | **388** / 1,509MB | 없음(279샘플 0.0%) | **POSSIBLE** |

단위는 논리 CPU 개수(8개 중)다. 구간은 샘플러 시작~끝이라 측정 전 웜업 2분을 포함한다. 판정에 쓴 네 측정은 모두 `CONTAMINATION: NONE`이다.

## 9. 판정

| 항목 | 기준 | 개선 후 | 판정 |
|---|---|---|---|
| solo map_pins P95 | ≤ 300ms | 81.4ms | 합격 |
| solo map_pins dropped | 0 | 0 | 합격 |
| 지도 핀 쿼리 mixed DB 시간 비중 | 줄어야 한다 | 37.5% → 7.3% | 줄었다 |
| mixed map_pins P95 | ≤ 300ms | 7,379.7ms | **불합격** |
| 응답 동일성 | EXCEPT ALL 0행 | 4방향 0 (2회) | 같다 |

**#220은 solo 합격과 지도 핀 DB 시간 비중 감소로 완료한다. mixed 판정은 #221~#225 이후로 미룬다**(2026-11-26 사람 결정. 계약 완료 조건을 이 기준으로 바꿨다).
mixed 불합격은 지도 핀 쿼리 자체가 아니라, 안 읽은 알림 수(32.7%)·피드 수 `count(distinct)`(20.8%) 등 다른 쿼리가 DB를 점유해 생긴 풀 대기 때문이다(7-3). 지도 핀 쿼리 평균은 mixed에서도 34.4ms다.

feedCount 상한(99+)은 넣지 않는다. 지도 핀의 DB 시간 비중이 7.3%라 지금은 개수 세는 비용을 줄일 필요가 없다(계약 결정 사항의 "개수 세는 비용이 크게 남으면 별도 이슈" 조건에 해당하지 않음).

## 10. 남은 리스크

- **일반 팀 회귀**: 일반 팀 4에서 1.19 → 2.66ms(4-1). 플래너가 장소 그룹 수를 186으로 추정(실제 22)해 place seq scan + hash join을 고른다. 2,000개 일반 팀 대부분이 이 형태일 것으로 추정한다(팀 4만 확인). #220에서는 고치지 않는다(사람 결정). 운영처럼 place가 크면 nested loop을 고를 것으로 추정하지만 확인하지 않았다.
- **운영 인덱스 생성 중 쓰기 차단**: 시드 100만 행에서 1.812초(3절). 운영 feed 행 수와 운영 DB에서의 시간은 미확인이다.
- **index-only scan은 visibility map에 기댄다**: 이번 EXPLAIN은 모두 heap fetch 0이다. 시드는 `VACUUM` 뒤이고 soft delete가 0건이다(베이스라인 9절). 운영의 autovacuum 상태에서는 heap fetch가 생길 수 있다(미확인).
- **큰 팀 개수 세기**: 큰 팀 1은 지금도 V18 인덱스 항목 60,147개를 매번 읽는다(GroupAggregate 약 10ms, 4-1). 피드가 더 많아지면 그만큼 늘어난다.
- **두 인덱스 공존**: V18과 V26이 #224 머지 전까지 함께 있다. 피드 쓰기마다 인덱스 갱신이 하나 더 있다(쓰기 비용은 재지 않았다).

## 11. 다음 작업

- #224(핀 선택 피드 목록): 이 측정 뒤 볼륨을 그대로 두었으므로 이 DB(V26)가 #224의 개선 전 상태다. #224에서 V26 상태의 #224 쿼리 계획을 EXPLAIN으로 확인하고 V18을 DROP(V27)한다. 이때 L의 개수 GROUP BY가 V18 대신 V26을 쓰게 될 것으로 보므로(추정, 미확인) 지도 핀 EXPLAIN도 다시 잰다.
- #221(안 읽은 알림 수), #222(피드 수 `count(distinct)`), #223(피드 목록 커서), #224, #225(best-attendance) 뒤 mixed로 다시 판정한다.
- 일반 팀 회귀를 고치려면(필요해지면) 플래너가 place를 `place_pkey`로 찾게 하는 형태를 따로 검토한다(이번에는 범위 밖).

## 12. 원시 파일 목록 (`loadtest/results/raw/`)

| 파일 | 내용 |
|---|---|
| `app-seed.log`, `seed-00_common.log`, `seed-10_feed.log`, `seed-20_notification.log` | Flyway V1~V25 기동 로그, 시드 출력과 확인 쿼리 |
| `20261126-1524-lock1-explain-team1.txt`, `-team4.txt`, `-lock1-compare.txt` | 잠금 1: 트랜잭션 안 V26 EXPLAIN(개선 전 SQL·L·D)과 전체 팀 비교 |
| `20261126-1527-*` | 폐기한 잠금 2 첫 시도(개선 전 앱 로그·EXPLAIN·solo) |
| `20261126-1554-before-app.log`, `-before-explain-team{1,4}.txt`, `-before-solo-*`, `-before-mixed-*` | 잠금 2 개선 전(V25) |
| `20261126-1554-after-app.log`, `-after-explain-team{1,4}.txt`, `-after-compare.txt`, `-after-solo-*`, `-after-mixed-*` | 잠금 2 개선 후(V26) |
| `jar/before.jar`, `jar/after.jar` | 측정에 쓴 jar |

측정마다 `-k6-summary.json`, `-k6.log`, `-hikari.csv`, `-pgss.{txt,csv}`, `-contamination.txt`, `-load-{host,docker}.csv`, `-load-meta.txt`, 측정 전 웜업 `-prewarm-k6*`가 있다.
작업 트리 밖: Prometheus 데이터 복사본 `C:\Users\06har\Documents\tikitak-loadtest-220-prometheus-20261126-1554`.
