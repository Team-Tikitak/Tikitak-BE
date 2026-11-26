# 지도 핀 조회는 V26 인덱스 + 장소별 LATERAL LIMIT 1(후보 L)로 바꾸고, V18 DROP과 feedCount 상한은 미룬다

- **날짜**: 2026-11-26
- **관련 이슈**: #220 (계약 `docs/contracts/2026-11-26-map-pin-latest-index.md`, 측정 결과 `loadtest/results/2026-11-26-map-pins-index.md`)
- **상태**: 결정됨 (2026-11-26 사람 결정. 쿼리 형태 L은 계약 규칙대로 EXPLAIN으로 골랐다)

## 상황 (Context)
- `FeedRepository.findMapPinsByTeamId`는 `WITH ranked` 윈도 함수 쿼리였다. 팀의 장소 있는 활성 피드를 전부 읽고 `place_id, created_at DESC, id DESC`로 정렬한 뒤 장소마다 1행만 남겼다.
- 베이스라인(`loadtest/results/2026-11-24-baseline.md` 7-1, 8절 이슈 1)에서 큰 팀 EXPLAIN 117.6ms, mixed DB 시간의 38.0%, solo 20 RPS에서도 P95 6,301.3ms였다.
- 이번 시드에서 큰 팀 1은 장소 있는 활성 피드 60,147건, 장소 287개다(`raw/seed-10_feed.log`).

## 검토한 선택지 (Options considered)

### 설계 (계약 결정 사항, 2026-11-26 사람 선택)
| 선택지 | 내용 | 문제 |
|---|---|---|
| **(a) 인덱스 + 쿼리 재작성 (채택)** | V26 `(team_id, place_id, created_at DESC, id DESC) WHERE deleted_at IS NULL AND place_id IS NOT NULL` + 장소별 최신 1건을 인덱스로 찾기 | 아래 "남은 리스크" |
| (b) 집계 테이블 | 팀·장소별 개수와 대표 피드를 따로 저장 | 피드 쓰기 경로(작성·삭제·장소 변경)를 전부 고쳐야 하고 값이 어긋날 위험이 있다 |
| (c) 캐시 | 짧은 TTL 캐시 | 반영이 늦고, 캐시가 빈 요청은 지금처럼 느리다 |

### 쿼리 형태 (계약: 큰 팀 1에서 실행 시간이 짧은 쪽)
| 후보 | 내용 | 큰 팀 1 | 일반 팀 4 |
|---|---|---|---|
| **L (채택)** | `GROUP BY place_id`로 개수 + 장소마다 `CROSS JOIN LATERAL (… ORDER BY created_at DESC, id DESC LIMIT 1)` | **15.3ms** (2,179버퍼) | 3.26ms (258버퍼) |
| D | `DISTINCT ON (place_id) … ORDER BY place_id, created_at DESC, id DESC` + 같은 조건 `GROUP BY`를 place_id로 join | 27.3ms (1,703버퍼) | **1.52ms** (196버퍼) |

값은 잠금 1(`BEGIN; V26; EXPLAIN ×2; ROLLBACK`, `indcheckxmin` f) 2회차다(`raw/20261126-1524-lock1-explain-team{1,4}.txt`). V26을 실제로 적용한 뒤(잠금 2 개선 후) 다시 재도 큰 팀 1은 L 15.8 / D 21.7ms, 일반 팀 4는 L 2.66 / D 2.62ms였다(`raw/20261126-1554-after-explain-team{1,4}.txt`).

### 인덱스 생성 방식
| 선택지 | 문제 |
|---|---|
| **일반 `CREATE INDEX` (채택, V18·V25와 같은 형식)** | 생성하는 동안 feed 쓰기가 기다린다 |
| `CREATE INDEX CONCURRENTLY` | `executeInTransaction=false`가 필요하다. 실패하면 INVALID 인덱스가 남아 수동 정리가 필요하다. Flyway 락 문제를 다시 끌어온다 |

## 결정 (Decision)
- **V26** `src/main/resources/db/migration/V26__add_feed_team_place_latest_index.sql`: 위 인덱스 한 문장. `-- flyway:executeInTransaction=false`는 쓰지 않는다.
- **쿼리**: `findMapPinsByTeamId`의 SQL만 L로 바꾼다. `MapPinRow`의 컬럼·별칭, `MapService`, DTO는 그대로다. `ORDER BY`는 넣지 않는다(핀 순서는 이전에도 정해져 있지 않았다). `feed_image`는 그대로 `LEFT JOIN … order_index = 0`이다.
- **V18 `idx_feed_map_pin_lookup`은 #220에서 지우지 않는다.** #224(V27)에서 EXPLAIN으로 확인한 뒤 DROP한다.
- **feedCount 상한(99+)은 넣지 않는다.**

## 이유 (Rationale)
- **L을 고른 이유**
  - 계약 기준(큰 팀 1 실행 시간)에서 L이 빨랐다(15.3 vs 27.3ms, 잠금 1). 지연과 CPU를 쓰는 것은 큰 팀이다(베이스라인 7-2).
  - L은 장소마다 `idx_feed_team_place_latest`에서 1행만 읽는다(287회 × 1행, 862버퍼). D는 같은 인덱스를 팀 전체(60,147행)만큼 순서대로 읽고 `Unique`로 고른다(366버퍼). 피드가 늘면 D의 최신 1건 단계는 피드 수에 비례하고, L은 장소 수에 비례한다.
  - 일반 팀 4에서는 D가 빨랐다(1.52 vs 3.26ms, 잠금 1). 차이는 약 1.7ms이고, 큰 팀에서 L이 아끼는 시간(약 12ms)보다 작다. 이 역전은 기록만 하고 형태 선택은 계약 기준을 따랐다.
- **인덱스만 추가하고 쿼리를 그대로 두지 않은 이유**: V26만 있어도 개선 전 SQL은 129.5 → 49.2ms로 빨라진다(새 인덱스 Index Only Scan이 bitmap heap scan과 정렬을 대신한다, `-1554-after-explain-team1.txt` (i)). 그래도 60,147행 전체에 WindowAgg를 돌려 L(15.8ms)보다 약 3배 느리다.
- **일반 `CREATE INDEX`를 고른 이유**
  - 시드 feed 1,000,287행에서 생성 시간이 1.812초였다(Flyway `execution time`, `raw/20261126-1554-after-app.log`). 쓰기가 막히는 시간이 이 정도라면 짧다고 보았다. 운영 feed 행 수와 운영 DB에서의 생성 시간은 미확인이다.
  - 트랜잭션 안에서 실행되므로 실패하면 인덱스 없이 롤백된다. CONCURRENTLY는 INVALID 인덱스가 남는다.
  - CONCURRENTLY는 [CREATE INDEX CONCURRENTLY + Flyway 데드락](2026-09-21-flyway-concurrent-index-deadlock.md)의 `executeInTransaction=false` 경로를 다시 쓰게 한다. 같은 이유로 V25도 일반 인덱스로 만들었다([회원 소셜 계정 부분 unique 인덱스](2026-11-09-member-social-unique-retry.md)).
- **V18 DROP을 #224(V27)로 미룬 이유**
  - 지금 L의 개수 `GROUP BY`가 V18을 쓴다. 개선 후 EXPLAIN에서 GroupAggregate 아래가 `Index Only Scan using idx_feed_map_pin_lookup`(큰 팀 1: 60,147행, 70버퍼)이다(`raw/20261126-1554-after-explain-team{1,4}.txt` (ii)). 더 작은 인덱스라 플래너가 고른 것으로 본다. V18을 지우면 이 단계가 V26(366버퍼)으로 옮겨 갈 것으로 본다(추정, 미확인). 그래서 #224에서 지도 핀도 다시 잰다.
  - #224 쿼리(핀 선택 피드 목록, `FeedRepositoryCustomImpl`)는 베이스라인 7-1(V25 상태)에서 V18 bitmap을 썼다. V26이 생긴 뒤 #224 쿼리가 어느 인덱스를 쓰는지는 이번에 EXPLAIN하지 않았다(미확인). #224에서 V26 상태로 확인하고 DROP한다.
  - #220과 #224는 PR을 나누므로(계약 결정 사항), 각 PR에서 바뀌는 계획을 따로 본다.
- **feedCount 상한(99+)을 넣지 않은 이유**
  - 지도 핀 UI가 feedCount를 실제로 표시하므로 API 의미가 바뀐다. #224 목록 `totalCount`와 숫자가 어긋날 수 있어 프론트 합의가 필요하다(계약 결정 사항).
  - 계약은 "개선 후 측정에서 목표를 못 맞추거나 개수 세는 비용이 크게 남으면 별도 이슈"로 정했다. 개선 후 지도 핀의 mixed DB 시간 비중은 7.3%(37.5%에서 줄어듦), solo는 합격했다(결과 문서 6·7절). 큰 팀 1의 개수 단계는 약 10ms다(GroupAggregate, 4-1). 지금은 개수 세는 비용을 줄일 필요가 없다고 보고 보류한다(2026-11-26 사람 결정).

## 검증
- `MapPinQueryIntegrationTest`(Postgres, Flyway 적용 스키마, 커밋 1): 개선 전 SQL을 문자열로 두고 `findMapPinsByTeamId`와 externalPlaceId 기준 모든 필드를 비교한다(`sameRowsAsBeforeSql`). 같은 created_at의 id 순서, 삭제된 최신 피드, 삭제 피드만 있는 장소, 장소 없는 피드, 다른 팀, 이미지 없는 대표 피드, order_index 1만 있는 대표 피드를 넣었고, 시각 고정이 실제로 걸렸는지 따로 단언한다(`createdAtOverrideIsApplied`).
- 시드 DB 전체 2,003팀: 개선 전 SQL·L·D가 모두 31,008행, `EXCEPT ALL` 4방향 0행(잠금 1과 잠금 2 개선 후 두 번, 결과 문서 5절).
- 측정(결과 문서): solo map_pins P95 4,444.0 → 81.4ms, dropped 18 → 0. mixed 지도 핀 DB 시간 비중 37.5% → 7.3%. mixed map_pins P95 12,556.9 → 7,379.7ms(불합격, #221~#225 뒤 다시 판정).

## 트레이드오프 / 남은 리스크
- **일반 팀이 느려졌다.** 일반 팀 4: 1.19 → 2.66ms(잠금 2 2회차). 플래너가 장소 그룹 수를 186으로 추정(실제 22)해 place 5,000행 seq scan + hash join을 고른다. 개선 전에는 전체 추정이 1행이라 `place_pkey` nested loop였다. #220에서는 고치지 않기로 했다(2026-11-26 사람 결정). 운영처럼 place가 크면 nested loop을 고를 것으로 추정하지만 확인하지 않았다.
- **운영 인덱스 생성 중 쓰기 차단**: blue/green 배포 중 새 인스턴스가 V26을 적용하는 동안, 기존 인스턴스의 피드 작성·삭제·장소 변경이 기다린다. 시드 100만 행 1.812초는 참고값이고, 운영 행 수는 미확인이다.
- **index-only scan은 visibility map에 기댄다**: 이번 EXPLAIN은 모두 heap fetch 0이었다. 시드는 VACUUM 뒤이고 soft delete가 0건이다. 운영의 autovacuum 상태에서는 heap fetch가 생길 수 있다(미확인).
- **큰 팀은 개수를 세려고 인덱스 항목 약 6만 개를 매번 읽는다**(약 10ms). 피드가 늘면 비례해 늘어난다. 지도 핀 비중이 다시 커지면 (b)·(c)나 feedCount 상한을 다시 본다.
- **V18과 V26이 #224 머지 전까지 함께 있다.** 피드 쓰기마다 갱신할 인덱스가 하나 더 있다(쓰기 비용은 재지 않았다).

## 다시 볼 조건
- #224에서 V18을 DROP할 때(지도 핀의 개수 단계가 V26으로 옮겨 가므로 지도 핀 EXPLAIN도 다시 잰다)
- #221~#225 뒤 mixed를 다시 쟀는데 지도 핀이 여전히 300ms를 넘을 때
- 일반 팀 지도 핀 지연이 문제로 보고되거나, 운영 EXPLAIN에서 place seq scan이 보일 때
