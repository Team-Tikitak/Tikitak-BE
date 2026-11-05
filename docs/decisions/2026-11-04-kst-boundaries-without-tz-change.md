# JVM 타임존은 바꾸지 않고 날짜·월 경계만 KST로 계산한다

- **날짜**: 2026-11-04
- **관련 이슈**: #162 (계약 `docs/contracts/2026-11-04-kst-time-boundaries.md`)
- **상태**: 결정됨, 적용 완료

## 상황 (Context)
- 이미지(`Dockerfile`), CI, 앱 설정 어디에도 타임존 설정이 없다. 운영 컨테이너의 JVM 타임존은 확정하지 못했다. 기본값은 UTC지만, 한국 홈서버에서 `/etc/localtime`을 마운트했다면 KST일 수 있다.
- DB 시각 컬럼은 전부 `TIMESTAMP`(타임존 없음)이고, 엔티티는 `LocalDateTime` + `@PrePersist`/`@CreatedDate`로 **JVM 타임존 기준 시각**을 저장한다.
- JVM이 UTC라면 다음 문제가 생긴다.
  - 매월 1일 KST 00~09시에는 "이번 달" 집계(모두의 픽, 홈)가 지난달로 계산된다.
  - 새벽 4시대로 의도한 배치가 KST 13시대에 실행된다.
  - `KstDateProvider.toKstDate`가 저장 시각을 KST로 간주해, KST 00~09시에 만든 팀의 데일리 질문 순번이 하루 밀린다.

## 검토한 선택지 (Options considered)
| 선택지 | 내용 | 문제 |
|---|---|---|
| A. JVM 타임존 고정 | `ENV TZ=Asia/Seoul` 또는 `-Duser.timezone` | 배포 시점부터 새 행은 KST, 기존 행은 UTC로 섞여 9시간 차이가 생긴다. 기존 `expiresAt`(refresh token, 초대, 업로드)이 9시간 일찍 만료된다. |
| A + 마이그레이션 | 모든 `TIMESTAMP` 컬럼에 +9h | prod blue/green 전환 중에는 구버전이 계속 UTC로 쓰므로 원자적으로 맞출 수 없다. 프론트가 응답 시각을 직접 보정하는지 확인하지 못해, 바꾸면 이중 보정 위험이 있다. |
| B. KST `Clock` 빈 주입 | `LocalDateTime.now(clock)` 전면 교체 | 저장 시각 불연속은 A와 같다. 엔티티 안의 `LocalDateTime.now()` 53곳(`domain/` grep 기준, `@PrePersist`/`@PreUpdate` 콜백 포함)은 빈을 주입하기 어렵고, `@CreatedDate`는 별도 `DateTimeProvider`가 필요하다. |
| **C. 경계만 KST 계산 (채택)** | 저장은 그대로 두고 "오늘/이번 달" 경계를 KST로 계산한 뒤 시스템 zone 시각으로 변환한다 | 응답 시각은 여전히 오프셋 없는 시스템 zone 시각이다. |

## 결정 (Decision)
- `KstDateProvider`가 시스템 zone(`ZoneId.systemDefault()`)을 생성자로 받는다.
  - `currentMonth()`와 `today()`는 KST 기준이다.
  - `startOfMonth(month)`는 KST 월 시작 시각을 시스템 zone 시각으로 변환해 돌려준다.
  - `toKstDate(dt)`는 저장 시각을 시스템 zone 시각으로 해석한 뒤 KST 날짜로 바꾼다.
- 월 집계 쿼리 범위(`FeedService`, `HomeService`)는 이 provider로 구한다.
- 모든 `@Scheduled`에 `zone = KstDateProvider.KST_ZONE_ID`를 명시한다.

## 이유 (Rationale)
- **데이터 마이그레이션과 저장 시각 불연속이 없다.** 저장 방식, 응답 형식, 만료 비교가 바뀌지 않는다.
- **서버 타임존과 관계없이 맞다.** 시스템 zone을 가정하지 않고 읽어 오므로 UTC든 KST든 같은 결과가 나오고, 운영 타임존을 확정하지 않아도 배포할 수 있다. 서버가 이미 KST라면 동작 변경이 없다.
- **테스트에서 고정할 수 있다.** Clock과 시스템 zone을 둘 다 주입하므로, JVM 기본 타임존과 관계없이 경계 시각을 테스트할 수 있다.

## 남은 리스크
- 네이티브 쿼리의 `current_timestamp`(`PlaceRepository`, `FeedReactionRepository`)는 DB 세션 타임존을 따른다. JVM 타임존과 다르면 이 컬럼만 기준이 다를 수 있다.
- API 응답의 `LocalDateTime`은 오프셋이 없는 시스템 zone 시각이다. 프론트가 이를 어떻게 해석하는지는 확인하지 못했다.
- 홈의 "모두의 픽"은 응답 `month`와 조회 범위를 각각 구하므로, KST 월이 바뀌는 순간에 한 달 어긋날 수 있다. 이번 변경 전에도 있던 구조다.
- prod blue/green 전환 중 두 컨테이너가 함께 떠 있으면 배치가 중복 실행될 수 있다. 분산 락은 없다.
- 새 코드가 `LocalDate.now()`/`YearMonth.now()`를 직접 쓰면 같은 문제가 다시 생긴다. ArchUnit 금지 규칙은 이번에도 만들지 않았다([결정 기록](2026-11-02-archunit-explicit-exceptions.md) 참고).

## 다시 볼 조건
- 저장 시각을 `timestamptz`/`Instant`로 바꾸거나, 응답에 오프셋을 넣기로 할 때
- 운영 JVM 타임존을 명시적으로 고정할 때 (그때는 위 마이그레이션 문제를 함께 결정)
