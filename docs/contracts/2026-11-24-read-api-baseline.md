# 작업 계약: 피드·알림 대용량 조회 API 측정 환경과 베이스라인

- **관련 이슈**: #217
- **브랜치**: `Test/#217-read-api-baseline` (origin/develop 931c22c에서 분기. Orca 브랜치에서 이름 변경 완료)
- **상태**: 승인됨 (2026-11-24), 베이스라인 측정 완료 (2026-11-24, 결과 `loadtest/results/2026-11-24-baseline.md`)

## 목표
로컬 Docker Postgres에 피드 약 100만 건과 회원당 알림 수백~수천 건을 쏠린 분포로 넣는다. 그 위에서 조회 API 6개를 같은 조건으로 측정해 베이스라인을 남긴다. 합격 기준을 넘은 API는 원인을 분석해 개선 이슈 후보로 정리한다. **앱 코드(`src/`)는 바꾸지 않는다.**

## 결정 사항 (2026-11-24 사람 선택)
- **자산 위치**: `loadtest/`를 저장소에 커밋한다. 결과는 `loadtest/results/`에 둔다. #215도 같은 구조를 쓴다.
- **토큰**: k6 `setup()`이 로컬 `JWT_SECRET`으로 HS256 access token을 직접 서명한다. 회원마다 토큰을 만든다. 형식은 `TokenService.java:201-216`과 같게 하고, 앱이 받아들이는지는 첫 커밋 검증에서 확인한다.
- **분포**: 큰 팀 3개(팀당 피드 10만 건, 멤버 6명)와 일반 팀 약 2,000개(팀당 평균 피드 350건, 멤버 3~6명)로 나눈다. 전체 피드는 약 100만 건이다.
- **합격 기준**: 혼합 시나리오를 **100 RPS 고정 도착률**(k6 `constant-arrival-rate`)로 5분간 유지한다(웜업 1분 별도). **API별 P95 300ms 이하, 실패율 1% 미만, `dropped_iterations` 0**이면 합격이다. 넘은 API만 개선 대상으로 삼는다. 대기 없는 VU 방식은 풀 10에서 항상 포화돼 쿼리 시간과 커넥션 대기가 섞이므로 쓰지 않는다(2026-11-24 사람 선택).
- **최대 처리량**: 같은 혼합 비중으로 RPS를 25 → 50 → 100 → 200 → 400으로 단계마다 2분씩 올린다. P95가 300ms를 처음 넘는 단계와 Hikari pending이 쌓이기 시작하는 단계를 기록한다. 판정용이 아니라 개선 전후 비교용이다.
- **메인 화면 경로**: 지도 진입은 `GET /teams/{teamId}/map/pins`, 핀 선택은 `GET /teams/{teamId}/feeds?placeId=<external_place_id>`다(`FeedController.java:53`, `FeedRepositoryCustomImpl.java:73`). 프론트가 이 두 API를 이 순서로 부르는 것을 사람이 확인했다(2026-11-24).

## 측정 환경 (고정값)
- Postgres: `loadtest/docker-compose.yml`
  - 이미지는 `postgres:17.6-alpine`이다. dev Supabase `SHOW server_version` 결과 17.6에 맞췄다(2026-11-24 사람 확인).
  - `shared_preload_libraries=pg_stat_statements`를 켠다.
  - 컨테이너 CPU와 메모리를 제한한다(예: 2 CPU, 2GB). 정확한 값은 README에 적는다.
  - `TZ`와 `PGTZ`를 `Asia/Seoul`로 둔다.
  - named volume을 쓴다. `docker compose down -v`로 초기화한다.
- Redis: 같은 compose에 둔다. 측정 대상 API는 Redis를 쓰지 않지만, 앱 기동에 필요할 수 있다(미확인).
- 앱:
  - `./gradlew bootJar`로 만든 jar를 `java -Duser.timezone=Asia/Seoul -jar`로 실행한다. devtools는 쓰지 않는다.
  - 프로필은 `dev`이고 env는 `loadtest/.env`에서 받는다. 템플릿은 `loadtest/.env.example`(가짜 R2·Kakao·OAuth 값)이다.
  - `SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=10`을 명시한다. 기본값과 같지만, 값을 고정해 두려는 것이다.
- 결과 문서에는 호스트 CPU·메모리, Docker 할당량(현재 8 CPU, 7.7GB), Postgres 버전과 주요 설정(`shared_buffers`, `work_mem`, `effective_cache_size`), 풀 크기를 함께 적는다.

## 시드 설계 (`loadtest/sql/`, psql로 순서대로 실행)
| 파일 | 내용 |
|---|---|
| `00_common.sql` | **#215와 공유.** member, team, team_member를 만든다. 큰 팀 3개와 일반 팀 N개(psql `-v`로 바꿀 수 있고 기본 2,000)를 만들고, 팀당 멤버 3~6명을 둔다. 회원은 ACTIVE이고 `onboarding_completed=true`, `profile_character_type`을 채운다. 앞의 회원 일부는 여러 팀에 동시에 속하게 한다. `team_member.profile_img_url`은 50%만 채운다(지연 로딩 쿼리가 실제처럼 섞이게). |
| `10_feed.sql` | place(약 5,000개, 지역 섞음), feed, feed_image, feed_tag, feed_reaction, feed_comment를 만든다. 아래 분포를 따른다. |
| `20_notification.sql` | 회원당 200~2,000건(합계 약 1,000만 건 목표)을 만든다. 70%는 읽음이다. type은 5종을 섞는다. team_id, feed_id, actor는 실제 팀·피드·멤버를 가리킨다. 최근 90일에 분포시킨다. |
| `30_targets.sql` | k6 입력 JSON을 뽑는다(`loadtest/k6/data/targets.json`, 커밋하지 않음). 들어가는 것: 팀별 회원 ID, 큰 팀 표시, 댓글이 많은 피드, 깊은 페이지 커서(큰 팀에서 1만·5만·9만 번째 피드의 `createdAt_id`), 팀별로 피드가 있는 place의 `external_place_id`와 피드 수(핀 선택 요청에 사용). |

피드 분포:
- 기간은 최근 2년이다. **이번 달 피드가 팀별 피드의 약 5%** 들어가게 한다.
- 약 20%는 데일리 답변 피드다(목표). **실제 전체 14.9%, 일반 팀 19.5%, 큰 팀 4.1%**다. 질문의 effective_from이 2025-01-01이고 `uk_feed_daily_answer_active_key` 때문에 멤버당 하루 1건이 상한이라 큰 팀은 채울 수 없다(2026-11-24 구현 결과). 데일리 피드는 실제 생성 경로(`createMyAnswer`)처럼 이미지 1장, 작성자 태그만, place 없음이다. `question_id`, `question_answer_date`, `daily_answer_active_key`를 실제 형식으로 채우고, 유니크 제약을 지킨다.
- 약 60%는 place가 있다(2026-11-24 사람 지정). 장소 선택은 한쪽으로 쏠리게 한다(인기 장소 소수에 피드가 몰림). 그래서 큰 팀에는 피드가 수천 건인 장소가 생긴다.
- 일반 피드마다 이미지 1~3장을 넣는다. `order_index`는 0부터 시작하고 중복되지 않게 한다. `media_id`는 NULL이고 `img_url`은 가짜 값이다.
- 태그에는 **작성자를 반드시 넣고**, 일반 피드는 다른 멤버 0~2명을 더한다.
- 반응은 0~4개다. 피드와 멤버 조합은 유니크하게 한다.
- 댓글은 피드의 80%에 0~3개를 넣는다. **피드의 0.1%는 댓글 500~2,000개**로 한다. `feed_image_id`와 position(0~1)을 채운다.
- soft delete된 피드는 넣지 않는다. `FeedCleanupBatchService`가 지워 버려 재현성이 깨지기 때문이다. 이 선택은 남은 리스크에 적는다.

시드 생성은 행 단위 루프가 아니라 `generate_series`로 한 번에 INSERT한다. 전체 시드 시간을 README에 적는다.

시드 트랜잭션 안에서만 `SET LOCAL session_replication_role = replica`(FK 트리거 끔)와 `work_mem=128MB`를 쓰고, 커밋 뒤 FK 고아 검사로 확인한다. 끝에서 `VACUUM (ANALYZE)`를 한다. `targets.json`의 큰 팀 장소는 피드 수 상위 100개만 담는다.

## k6 시나리오 (`loadtest/k6/read-api-baseline.js`)
- 호출 비중(지도 중심, 2026-11-24 사람 선택): 지도 핀 20%, 핀 선택 피드(`placeId`) 20%, 피드 첫 페이지 15%, 피드 깊은 페이지 5%, best-attendance 5%, 댓글 10%(댓글이 많은 피드 포함), 알림 목록 10%, 안 읽은 알림 수 15%. 핀 선택은 장소의 피드 수에 비례해 고른다.
- 요청의 30%는 큰 팀으로 보낸다. 회원은 그 팀의 멤버 중에서 무작위로 고른다.
- 요청마다 `api` 태그를 붙이고, threshold를 API별로 건다(`p(95)<300`, 실패율 `<1%`).
- `MODE=mixed`(기본): 웜업 1분(20 RPS), 그다음 `constant-arrival-rate` 100 RPS로 5분간 측정한다. `preAllocatedVUs` 50, `maxVUs` 300으로 둔다.
- `MODE=ramp`: `ramping-arrival-rate`로 25/50/100/200/400 RPS를 단계마다 2분씩 유지한다.
- `MODE=solo API=<이름>`: 한 API만 20 RPS로 1분간 돌린다. 요청당 쿼리 수를 셀 때 쓴다.
- 보조 스크립트 `loadtest/scripts/`
  - `pg_stat_statements` 초기화와 덤프
  - 측정 중 `/actuator/prometheus`의 `hikaricp_connections_active/pending`을 1초마다 기록

## 범위: 건드려도 되는 파일
- `loadtest/**` (새로 만든다)
- `.gitignore`: `loadtest/.env`, `loadtest/k6/data/`, `loadtest/results/raw/`를 추가한다.
- `docs/contracts/2026-11-24-read-api-baseline.md` (이 문서)
- `src/**`, `build.gradle`, `src/main/resources/**`는 **건드리지 않는다**. actuator 포트 분리는 측정용 `.env`의 env 값으로만 한다.

## 의도된 동작 변경
없음. 앱 코드와 설정을 바꾸지 않는다.

## 완료 조건
- [ ] `git diff --stat origin/develop...HEAD`(세 점, develop이 앞서 나가도 이 브랜치 변경만 보이게) 결과에 `src/`, `build.gradle`, `src/main/resources/` 변경이 없다.
- [ ] `docker compose -f loadtest/docker-compose.yml down -v` 다음 `up -d`를 하면 빈 DB가 뜬다. 앱을 기동하면 Flyway V1~V25가 적용된다(`flyway_schema_history` 25행).
- [ ] 시드 4개를 실행한 뒤 확인 쿼리(README에 포함) 결과가 아래 조건을 만족한다.
  - feed 총수가 90만~110만 건이다.
  - 큰 팀 3개의 피드가 각각 10만 건 ±1%다.
  - 이번 달 피드가 있다.
  - 피드마다 작성자 태그가 있다(작성자 태그가 없는 피드 0건).
  - `(feed_id, order_index)`가 중복되는 이미지가 0건이다.
  - 댓글이 500건 이상인 피드가 있다.
  - 회원당 알림이 200~2,000건이고 읽음 비율이 60~80%다.
- [ ] k6가 서명한 토큰으로 `GET /api/v1/me/notifications/unread-count`가 200을 돌려준다.
- [ ] `MODE=mixed` 실행에서 8개 요청 유형(핀 선택 피드, 피드 첫 페이지·깊은 페이지 각각 포함) 모두 4xx·5xx 없이 응답한다(의도한 응답이 200인지 확인). 핀 선택 응답에 피드가 1건 이상 들어 있다.
- [ ] `MODE=ramp` 결과에서 단계별 P95와 Hikari pending 최대값을 결과 문서에 표로 적는다.
- [ ] `loadtest/results/2026-11-xx-baseline.md`에 다음을 적는다: 환경, 시드 규모와 시드 시간, API별 P95·P99·처리량(mixed)과 단계별 결과(ramp), Hikari active/pending 최대값, solo 실행 기준 API별 요청당 쿼리 수와 상위 쿼리의 평균 시간(`pg_stat_statements`), 합격 여부.
- [ ] 기준을 넘은 API마다 대표 쿼리의 `EXPLAIN (ANALYZE, BUFFERS)` 결과와 원인 분석을 결과 문서에 적는다. 개선 이슈 초안도 함께 둔다. **이슈 생성 자체는 사람 승인을 받은 뒤 한다.**
- [ ] `loadtest/README.md`만 보고 처음부터(초기화 → 기동 → 시드 → 측정) 재현할 수 있다.

## 커밋 구성
1. 측정 환경과 공통 시드: `docker-compose.yml`, `.env.example`, `README.md`(기동·초기화), `sql/00_common.sql`, `.gitignore`
2. 피드·알림 시드와 대상 추출: `sql/10_feed.sql`, `20_notification.sql`, `30_targets.sql`, README의 시드 절차와 확인 쿼리
3. k6 시나리오와 수집 스크립트: `k6/read-api-baseline.js`, `scripts/*`, README의 측정 절차
4. 모니터링과 측정 규칙(2026-11-24 추가 승인): compose에 Prometheus·Grafana 추가, 기존 대시보드 JSON을 `loadtest/grafana/`에 두고 자동 로드, `.env.example`에 `MANAGEMENT_SERVER_PORT=8081`(actuator 분리), 측정 전 2분 웜업 규칙을 README와 래퍼에 반영
5. 베이스라인 결과: `results/2026-11-xx-baseline.md`, 계약 상태 갱신

## 하지 말 것
- dev Supabase나 prod DB에 접속하거나 시드를 넣는 것. 단, `SHOW server_version` 확인은 사람이 한다.
- 시드를 `db/migration`에 넣는 것
- 앱 코드나 설정을 바꾸는 것. dev 전용 토큰 엔드포인트도 만들지 않는다.
- 측정 중에 풀 크기나 Postgres 설정을 바꾸는 것
- 측정 도중 개선 작업을 하는 것(개선은 별도 이슈로 한다)
- 사람 승인 없이 커밋, push, 이슈 생성을 하는 것

## 남은 리스크
- 시드 시각이 실행 시점 `LOCALTIMESTAMP` 기준이라, 다시 넣으면 날짜 경계에 걸리는 데일리 답변 수가 몇 건 달라진다(148,958 → 148,967, 베이스라인 실행은 148,993). 개선 전후 비교는 같은 시드 실행 위에서 하거나 건수 차이를 결과에 적는다.
- k6 핀 선택은 큰 팀의 상위 100개 장소에서만 고르므로, 피드가 적은 꼬리 장소는 큰 팀에서 측정되지 않는다.
- soft delete 피드가 0건이라, `deleted_at IS NULL` 부분 인덱스와 비부분 인덱스를 고르는 실행 계획이 실제와 다를 수 있다.
- 로컬은 앱과 DB 사이 네트워크 지연이 거의 없다. 그래서 절대 수치는 dev/prod보다 낮게 나온다. 같은 조건에서 개선 전과 후를 비교하는 용도로만 쓴다.
- 혼합 시나리오에서 한 API의 커넥션 점유가 다른 API의 지연에 섞인다. 원인을 가를 때는 solo 실행과 `pg_stat_statements`로 나눠 본다.
- (확인됨) k6 `crypto.hmac(..., 'base64rawurl')`이 패딩 없는 base64url을 낸다. 이 토큰으로 unread-count 200을 받았다(커밋 3 스모크).
- OAuth placeholder를 비웠을 때 기동이 실패하는지, Redis가 없을 때 기동에 영향이 있는지 미확인이다. 그래서 `.env.example`에 가짜 값을 넣고 Redis도 띄운다.

## 참고
- 조사 근거: 각 API의 쿼리 경로와 수 (`FeedService.java:94-154`, `FeedRepositoryCustomImpl.java:39-62,103-110`, `FeedRepository.java:103-130`, `FeedTagRepository.java:15-33`, `FeedCommentRepositoryCustomImpl.java:29-42`, `NotificationRepositoryCustomImpl.java:27-56`), 토큰 검증 시 member 조회 (`TokenService.java:191`)
- `docs/decisions/2026-11-04-kst-boundaries-without-tz-change.md`: 시각은 시스템 zone으로 저장하고, 월 경계는 KST로 계산한다.
- `docs/decisions/2026-09-19-feed-n1-batch-fetch-pattern.md`, `docs/decisions/2026-11-19-querydsl-feed-list.md` (댓글 인덱스 사용 여부를 EXPLAIN으로 확인하지 않았다고 적혀 있음)
- lessons-log 2026-09-17(개인 DevController), 2026-09-19(N+1은 부하 테스트에서 발견)
- 예전 자산(커밋 안 됨): 메인 체크아웃 `loadtest/sql/seed_dev_data.sql`, `loadtest/k6/stress-test.js`
