# 작업 계약: 지도 핀 조회를 장소별 최신 1건 인덱스 탐색으로 바꾸기

- **관련 이슈**: #220
- **브랜치**: `Refactor/#220-map-pin-index` (origin/develop 2579dba 기준. Orca 브랜치에서 이름 변경 완료)
- **상태**: 승인됨 (2026-11-26)

## 목표
`GET /api/v1/teams/{teamId}/map/pins`가 쓰는 `FeedRepository.findMapPinsByTeamId`는 지금 팀의 장소 있는 피드를 전부 읽고 정렬합니다. 이것을 새 인덱스로 장소마다 최신 1건만 찾는 쿼리로 바꿉니다. **응답 내용(핀 집합, feedCount, 썸네일)은 바꾸지 않습니다.** 같은 시드 위에서 개선 전후를 측정해 결과를 남깁니다.

## 결정 사항 (2026-11-26 사람 선택)
- **설계**: (a) 인덱스 + 쿼리 재작성. 집계 테이블(b)은 쓰기 경로를 전부 고쳐야 하고 값이 어긋날 위험이 있어 기각했습니다. 캐시(c)는 반영이 늦고, 캐시가 빈 요청은 지금처럼 느려서 기각했습니다.
- **인덱스 생성**: 일반 `CREATE INDEX`(V18·V25 형식)로 만듭니다. `CONCURRENTLY`는 쓰지 않습니다. 대가는 생성 중 feed 쓰기가 막히는 것입니다. 남은 리스크에 적습니다.
- **V18 `idx_feed_map_pin_lookup`**: #220에서는 지우지 않습니다. #224 쿼리가 지금 이 인덱스를 씁니다. #224(V27)에서 EXPLAIN으로 확인한 뒤 DROP합니다.
- **두 이슈의 PR**: #220과 #224는 PR을 나눕니다. 같은 폴더에서 #220 다음에 #224를 이어서 합니다.
- **feedCount 상한(99+)**: 이번에는 넣지 않습니다. 지도 핀 UI가 feedCount를 실제로 표시하므로 API 의미가 바뀌고, #224 목록 `totalCount`와 숫자가 어긋날 수 있어 프론트 합의가 필요합니다. 개선 후 측정에서 목표를 못 맞추거나 개수 세는 비용이 크게 남으면 별도 이슈로 냅니다(2026-11-26 사람 결정).

## 쿼리 형태 (구현 때 EXPLAIN으로 하나 고름)
새 인덱스 `V26`:
```sql
CREATE INDEX idx_feed_team_place_latest
    ON feed (team_id, place_id, created_at DESC, id DESC)
    WHERE deleted_at IS NULL AND place_id IS NOT NULL;
```
후보 두 가지를 모두 같은 응답이 나오게 씁니다. 큰 팀 1에서 실행 시간이 짧은 쪽을 고르고, 그 이유와 두 계획을 결정 기록에 남깁니다.
- **L (LATERAL)**: 장소별 개수는 `GROUP BY place_id`로 셉니다(index-only scan 기대). 장소마다 `CROSS JOIN LATERAL (… ORDER BY created_at DESC, id DESC LIMIT 1)`로 최신 피드 id를 구합니다. 그다음 place와 `feed_image(order_index = 0)`를 지금과 같은 방식으로 join합니다.
- **D (DISTINCT ON)**: `DISTINCT ON (place_id) … ORDER BY place_id, created_at DESC, id DESC`로 최신 피드를 구하고, 개수는 같은 인덱스로 따로 셉니다.

지금과 같게 유지할 것:
- 활성 피드만 셉니다(`deleted_at IS NULL`). 장소 없는 피드는 뺍니다. 최신 기준은 `created_at DESC, id DESC`입니다.
- `feed_image`는 `LEFT JOIN … order_index = 0`입니다. 대표 피드에 이미지가 없으면 썸네일이 null입니다. order_index 0이 두 행이면 핀도 두 개가 되는 동작도 그대로 둡니다(현재 unique 제약 없음, V1:106-114·V8:10).
- `ORDER BY`를 넣지 않습니다. 지금도 핀 순서는 정해져 있지 않습니다.
- `MapPinRow`의 컬럼과 별칭, `MapService`·DTO는 바꾸지 않습니다.

## 의도된 동작 변경
- API 응답: **없음**. 핀 집합, 각 핀의 필드, feedCount, thumbnailUrl/heroPreviewUrl이 같아야 합니다. 핀 순서는 이전에도 정해져 있지 않았고, 이후에도 정하지 않습니다.
- 에러 경로: **없음**. `MapService.getMapPins`의 팀·멤버 검증(`MapService.java:30-48`)을 그대로 둡니다.
- DB: feed에 인덱스가 하나 늘어납니다. 피드 INSERT·soft delete·장소 변경 때 인덱스 갱신 비용이 하나 늘어납니다. V18을 DROP할 때까지(#224) 비슷한 인덱스 두 개가 함께 있습니다.
- 배포: 앱을 부팅할 때 Flyway가 V26을 적용합니다. 생성하는 동안 feed 쓰기가 기다립니다.

## 범위: 건드려도 되는 파일
- `src/main/resources/db/migration/V26__add_feed_team_place_latest_index.sql` (새 파일)
- `src/main/java/kusitms/spin/tikitak/repository/feed/FeedRepository.java` (`findMapPinsByTeamId`의 SQL만)
- `src/test/java/kusitms/spin/tikitak/repository/feed/FeedRepositoryIntegrationTest.java` 또는 새 `MapPinQueryIntegrationTest.java` (같은 패키지)
- `loadtest/sql/explain-map-pins.sql` (새 파일. 개선 전 쿼리, 후보 L·D의 EXPLAIN과 결과 비교 쿼리)
- `loadtest/results/2026-11-26-map-pins-index.md` (새 파일)
- `loadtest/README.md` (V1~V25 문구를 V26까지로 고치고, 결과 문서를 가리키는 한 줄)
- `docs/decisions/2026-11-26-map-pin-latest-index.md`, `docs/decisions/README.md` (인덱스 한 줄)
- 이 계약 파일

## 측정 절차
`loadtest/.env` 값: `LOADTEST_PROJECT=tikitak-loadtest-220`, PG 55434, Redis 56381, Prometheus 59092, Grafana 53002, `SERVER_PORT=8084`, `MANAGEMENT_SERVER_PORT=8085`. 볼륨을 지우기 전에는 `docker compose config`의 `name:`이 `tikitak-loadtest-220`인지 확인합니다.

사용자가 "#215 측정이 끝났고 develop으로 rebase했다"고 알려 주기 전에는 시드, gradle test, 빌드를 실행하지 않습니다.

1. **jar 두 개를 준비합니다(잠금 밖).** 개선 전 jar는 origin/develop 2579dba로 만들고, 개선 후 jar는 이 브랜치로 만듭니다. 각각 `build/` 밖 작업 폴더 경로에 이름을 나눠 복사합니다(예: `loadtest/results/raw/jar/before.jar`, gitignored).
2. **잠금 1: 시드와 쿼리 형태 선택.** `measure-lock.sh`로 감쌉니다.
   - 개선 전 jar로 기동해 Flyway V1~V25를 적용합니다. README 6~6-2 시드와 대상 추출을 실행합니다.
   - 데일리 답변 건수를 기록하고, 베이스라인 148,993건과의 차이를 결과 문서에 적습니다.
   - 앱을 끈 뒤 psql에서 `BEGIN; CREATE INDEX …; EXPLAIN (ANALYZE, BUFFERS)` 후보 L·D(큰 팀 1, 일반 팀 4, 각 2회)를 실행하고 `ROLLBACK`합니다. DB는 V25 상태로 돌아갑니다.
   - 고른 형태로 개선 후 jar를 만듭니다. 이 빌드는 잠금 밖에서 해도 됩니다.
3. **잠금 2: 개선 전 → 개선 후 연달아 측정.**
   - 개선 전 jar로 측정합니다: EXPLAIN(큰 팀 1·일반 팀 4, 2회차 기록), `run-measure.sh solo map_pins`, `run-measure.sh mixed`.
   - 앱을 끄고 개선 후 jar로 기동합니다. Flyway V26 적용 시간을 기록합니다(약 100만 행 feed의 인덱스 생성 시간, app.log의 Flyway 로그).
   - 개선 후 측정도 같은 항목으로 합니다.
   - 전체 팀에 대해 개선 전 SQL과 개선 후 SQL 결과를 `EXCEPT`로 양방향 비교합니다. 0행이어야 합니다.
   - 잠금 안에서 측정이 끝나면 Prometheus 스냅샷이나 패널 스크린샷을 작업 트리 밖에 저장합니다(lessons-log 59행).
4. 측정 뒤 볼륨은 지우지 않습니다. #224의 개선 전 상태(= #220 개선 후)로 그대로 씁니다.

ramp는 이번에 재지 않습니다. 결과 문서에 "미측정"으로 적습니다.

## 완료 조건
- [ ] `./gradlew test`, `./gradlew integrationTest`, `./gradlew spotlessCheck` 통과
- [ ] V26이 integrationTest의 Flyway에서 적용됩니다(`validate-on-migrate`, `ddl-auto: validate`). 파일이 일반 `CREATE INDEX`이고 `executeInTransaction=false`가 없습니다.
- [ ] 비교 통합 테스트: 개선 전 SQL을 테스트 안에 문자열로 두고, `findMapPinsByTeamId` 결과와 externalPlaceId를 키로 모든 필드를 비교합니다. 시나리오에는 다음이 들어갑니다.
  - 장소 여러 개
  - created_at이 같은 피드끼리 id로 최신을 가리는 경우
  - 삭제된 피드가 최신인 장소
  - 삭제 피드만 있는 장소(핀 없음)
  - 장소 없는 피드
  - 다른 팀 피드
  - 대표 피드에 이미지가 없는 경우
  - 대표 피드에 order_index 1만 있는 경우
  - 시각 고정이 실제로 걸렸는지 확인하는 단언(lessons-log 43행)
- [ ] 기존 `FeedRepositoryIntegrationTest`의 지도 핀 테스트 4건이 수정 없이 통과합니다.
- [ ] 시드 DB에서 전체 팀 개선 전후 결과 `EXCEPT` 양방향 0행입니다(결과 문서에 쿼리와 결과).
- [ ] 결과 문서에 개선 전·후 값이 모두 있습니다. 집계값마다 원시 파일·구간·표본 수를 적습니다(lessons-log 55행).
  - 큰 팀 1·일반 팀 4 EXPLAIN 실행 시간, 읽은 행, 버퍼
  - solo map_pins P95와 dropped
  - mixed의 map_pins P95와 지도 핀 쿼리의 DB 시간 비중
  - V26 생성 시간
  - 데일리 답변 건수
- [ ] 목표는 mixed map_pins P95 300ms 이하, solo map_pins dropped 0입니다. 못 맞추면 결과를 그대로 적고, 설계를 바꾸기 전에 멈추고 사람에게 보고합니다.
- [ ] 결정 기록에 다음을 적습니다: L·D 비교와 선택 이유, 일반 CREATE INDEX를 고른 이유와 쓰기 차단 리스크, V18 DROP을 #224로 미룬 이유.

## 커밋 구성
1. V26 마이그레이션 + `findMapPinsByTeamId` SQL 교체 + 비교 통합 테스트 + 이 계약
2. `loadtest/sql/explain-map-pins.sql` + 결과 문서 + README 수정 + 결정 기록·인덱스

## 남은 리스크
- **운영 인덱스 생성 중 쓰기 차단**: 운영 feed 행 수는 미확인입니다. blue/green 배포 중 새 인스턴스가 V26을 적용하는 동안, 기존 인스턴스의 피드 작성·삭제가 기다립니다. 시드(약 100만 행)에서 잰 생성 시간을 참고값으로 결과 문서에 적습니다.
- **index-only count는 visibility map에 기대고 있습니다**: 시드는 `VACUUM (ANALYZE)` 뒤라 heap fetch가 적을 수 있습니다. 운영은 autovacuum 상태에 따라 다릅니다(미확인).
- **큰 팀은 개수를 세려고 인덱스 항목 약 6만 개를 계속 읽습니다.** 300ms 목표를 못 맞추면 (b)나 (c)를 다시 검토합니다.
- 두 인덱스(V18, V26)가 #224 머지 전까지 함께 있습니다.

## 하지 말 것
- 범위 밖 리팩터링, 손으로 하는 포맷 정리(Spotless 결과는 허용)
- `MapService`, DTO, `MapPinRow` 변경. 핀 순서(`ORDER BY`) 추가
- V18 인덱스 DROP, `CONCURRENTLY` 사용
- #224 쿼리(`FeedRepositoryCustomImpl`) 변경
- 사용자 알림 전 시드·gradle·빌드 실행. 잠금 없이 시드나 측정 실행
- `docker compose down -v`. DB를 다시 시드할 때는 README 7번대로 `pgdata` 볼륨만 지웁니다
- 사람 승인 없이 커밋·푸시

## 참고
- 코드: `FeedRepository.java:103-130`, `MapPinRow.java:5-13`, `MapService.java:30-48`
- 인덱스: V11:7-9, V18:1-3. 데드락 결정은 `docs/decisions/2026-09-21-flyway-concurrent-index-deadlock.md`, CONCURRENTLY 기각 선례는 `docs/decisions/2026-11-09-member-social-unique-retry.md`
- 베이스라인: `loadtest/results/2026-11-24-baseline.md` 7절(EXPLAIN 271-272행), 8절 이슈 1
- lessons-log 43, 55, 56, 58, 59행
