# 작업 계약: 월/일 경계 KST 계산 및 스케줄러 zone 명시

- **관련 이슈**: #162
- **브랜치**: `Fix/#162-kst-zone` (Orca worktree, develop 09fcdf4 기준)
- **상태**: 완료 (2026-11-05, PR #175)

## 목표
- JVM 기본 타임존과 저장 방식은 그대로 둔다. "이번 달"과 "오늘" 경계만 KST로 계산하고, 계산한 경계를 시스템 zone 시각으로 바꿔 쿼리에 넣는다(방식 C, 2026-11-04 사람이 결정). 데이터 마이그레이션은 하지 않는다.
- 모든 `@Scheduled` cron이 JVM 타임존과 관계없이 KST 기준으로 실행되게 한다.
- 서버 zone은 확정하지 못했다. 컨테이너는 UTC로 추정하지만, 한국 홈서버라 `/etc/localtime`이 마운트돼 있으면 KST일 수 있다. 그래서 구현이 **어느 zone에서 돌아도 맞게** 만든다. 시스템 zone을 상수로 가정하지 않고 `ZoneId.systemDefault()`로 읽는다.

## 항목별 작업

| # | 작업 |
|---|---|
| 1 | `KstDateProvider` 확장 |
|   | - `public static final String KST_ZONE_ID = "Asia/Seoul"`을 추가하고, `KST = ZoneId.of(KST_ZONE_ID)`로 둔다. |
|   | - 생성자를 `(Clock clock, ZoneId systemZone)`로 바꾼다. 기본 생성자는 `(Clock.system(KST), ZoneId.systemDefault())`다. |
|   | - 추가 메서드: `YearMonth currentMonth()`(KST 기준), `LocalDateTime startOfMonth(YearMonth)`(KST 월 시작 시각을 시스템 zone 벽시계로 변환한 값). 다음 달 시작은 `startOfMonth(month.plusMonths(1))`로 구한다. |
|   | - `toKstDate(LocalDateTime)`의 입력을 **시스템 zone 벽시계**로 해석하도록 고친다: `ZonedDateTime.of(dt, systemZone).withZoneSameInstant(KST).toLocalDate()`. |
| 2 | `FeedService.getEveryonePickItems`/`getEveryonePickThumbnailItem`과 `HomeService`의 `YearMonth.now()` 10곳을 `KstDateProvider`로 바꾼다. 월 범위 계산과 응답의 `month` 값이 모두 대상이다. 두 서비스에 `KstDateProvider`를 생성자로 주입한다. |
| 3 | `@Scheduled` 5개(`TeamInactiveBatchService`, `MediaCleanupBatchService`, `WithdrawnMemberProfileImageCleanupBatchService`, `FeedCleanupBatchService`, `ObjectDeleteOutboxRetryBatchService`)에 `zone = KstDateProvider.KST_ZONE_ID`를 추가한다. cron 값은 그대로 둔다. |
| 4 | 결정 기록 `docs/decisions/2026-11-04-kst-boundaries-without-tz-change.md`와 README 목록 한 줄을 추가한다. A/B를 기각한 이유(9시간 불연속, blue/green 마이그레이션 위험, 프론트 보정 여부 미확인)와 남은 리스크를 적는다. |

## 의도된 동작 변경
"시스템 zone = UTC"이면 아래처럼 바뀐다. 시스템 zone이 이미 KST이면 동작 변경은 없다.

| 경우 | 이전 | 이후 |
|---|---|---|
| 모두의 픽 / 홈 월 집계, KST 매월 1일 00:00~09:00 | 지난달 범위, `month` = 지난달 | 이번 달 범위, `month` = 이번 달 |
| 월 범위 경계 | UTC 1일 00:00 | KST 1일 00:00 (= UTC 전날 15:00) |
| 데일리 질문 순번, KST 00~09시에 만든 팀 | 생성일을 하루 이르게 계산해 순번이 +1 | 실제 KST 생성일 기준. **기존 팀은 배포 후 오늘 질문이 한 칸 앞 질문으로 바뀐다** |
| 배치 4개 (04:00, 04:15, 04:30) | KST 13:00~13:30 | KST 04:00~04:30 |
| Outbox 재시도 (30분마다) | 변화 없음 | 변화 없음 |

- 저장되는 시각, API 응답의 시각 문자열, 만료 비교는 바뀌지 않는다.

## 범위: 건드려도 되는 파일
- `src/main/java/kusitms/spin/tikitak/global/time/KstDateProvider.java`
- `src/main/java/kusitms/spin/tikitak/service/feed/FeedService.java`
- `src/main/java/kusitms/spin/tikitak/service/home/HomeService.java`
- 위 배치 서비스 5개
- `src/test/java/kusitms/spin/tikitak/global/time/KstDateProviderTest.java` (신규)
- `src/test/java/kusitms/spin/tikitak/service/feed/FeedServiceTest.java`, `service/home/HomeServiceTest.java`, `service/dailyquestion/DailyQuestionServiceTest.java`
- 위 서비스의 생성자 변경 때문에 컴파일이 깨지는 다른 테스트(생성자 인자 추가만 허용)
- `docs/decisions/2026-11-04-kst-boundaries-without-tz-change.md`, `docs/decisions/README.md`

## 완료 조건
- [ ] `./gradlew test` 통과, `./gradlew spotlessCheck` 통과(ArchUnit 포함)
- [ ] main 코드에 `YearMonth.now()` 호출이 0건이다.
- [ ] `@Scheduled` 5개가 모두 `zone = KstDateProvider.KST_ZONE_ID`를 가진다.
- [ ] `KstDateProviderTest`: 시스템 zone이 UTC일 때
  - Clock `2026-11-30T14:59:59Z`이면 `currentMonth()` = 2026-11이다.
  - Clock `2026-11-30T15:00:00Z`이면 `currentMonth()` = 2026-12이다.
  - `startOfMonth(2026-12)` = `2026-11-30T15:00`이다.
  - `toKstDate(2026-03-03T15:00)` = 2026-03-04, `toKstDate(2026-03-03T14:59:59)` = 2026-03-03이다.
  - 시스템 zone이 KST일 때 `startOfMonth`와 `toKstDate`가 입력의 날짜를 그대로 쓴다.
- [ ] `FeedServiceTest`: KST 12월 1일 00:30에 고정한 Clock에서 `getEveryonePickItems`가 `(2026-11-30T15:00, 2026-12-31T15:00)` 범위로 repository를 호출하는지 `verify`로 확인한다(시스템 zone UTC).
- [ ] `HomeServiceTest`: 같은 경계 시각에서 월 범위 인자와 응답 `month` = 12를 확인한다. 기존 `YearMonth.now()` 기반 단언은 고정 Clock 기준으로 바꾼다.
- [ ] `DailyQuestionServiceTest` 회귀 테스트: 시스템 zone UTC에서 팀 `createdAt` = `2026-03-03T16:00`(KST 03-04 01:00)이고 오늘이 KST 2026-03-04이면, 경과 0일의 질문(인덱스 0)을 반환한다.
- [ ] 테스트는 JVM 기본 타임존에 의존하지 않는다. 시스템 zone을 생성자로 주입하고 `TimeZone.setDefault`는 쓰지 않는다.

## 커밋 구성
1. `KstDateProvider` 확장, `KstDateProviderTest`, `DailyQuestionServiceTest` 회귀 테스트(`toKstDate` 보정)
2. `FeedService`/`HomeService`의 월 경계를 KST로 계산 + 테스트
3. `@Scheduled` zone 명시
4. 결정 기록 + README

## 하지 말 것
- Dockerfile/JVM 타임존 변경, `TimeZone.setDefault`, Flyway 마이그레이션, 엔티티 `LocalDateTime.now()` 교체, 응답 DTO 직렬화 형식 변경
- cron 값 자체 변경, ShedLock 등 중복 실행 방지 도입(범위 밖)
- 범위 밖 리팩터링, 손으로 하는 포맷 정리(Spotless 결과는 허용)
- 사람 승인 없이 커밋/푸시

## 참고
- 조사: 2026-11-04 researcher. DB 시각 컬럼은 전부 `TIMESTAMP`(타임존 없음), 엔티티는 `LocalDateTime` + `@PrePersist`/`@CreatedDate`로 시스템 zone 벽시계를 저장한다.
- 쿼리 대상 `f.createdAt`은 `Feed` `@PrePersist`가 넣는 값이라 시스템 zone 벽시계다. 따라서 경계도 시스템 zone으로 변환해야 맞다.
- 남은 리스크(PR에 기록): 네이티브 `current_timestamp`(`PlaceRepository:19`, `FeedReactionRepository:22`)는 DB 세션 TZ를 따른다. 프론트가 오프셋 없는 응답을 어떻게 해석하는지 확인하지 못했다. prod blue/green에서 배치가 중복 실행될 가능성이 있다.
- `docs/decisions/2026-11-02-archunit-explicit-exceptions.md:28`: `LocalDateTime.now()` 금지 규칙은 이번에도 만들지 않는다.
