# ArchUnit 규칙의 기존 위반은 동결(FreezingArchRule) 대신 명시적 예외 목록으로 허용한다

- **날짜**: 2026-11-02
- **관련 이슈**: 없음 (작업 하네스 도입 후속, 계약 `docs/contracts/2026-11-02-archunit-rules.md`)
- **상태**: 결정됨, 적용 완료

## 상황 (Context)
`CLAUDE.md`의 Architecture/Conventions 규칙은 글로만 있어서, 사람이든 AI 워커든 어겨도 `./gradlew test`가 통과했다. 기계적으로 판정할 수 있는 규칙을 ArchUnit 테스트(`src/test/java/kusitms/spin/tikitak/architecture/`)로 만들기로 했다. 문제는 이미 코드에 있는 위반이다. 예를 들어 `domain.notification.entity.Notification`은 `service.notification.dto.NotificationPayload`를 쓰고, `repository.feed.FeedRepository`는 service 패키지의 projection(`RegionRow`, `MapPinRow`)을 반환한다. 이번 작업에서는 프로덕션 코드를 고치지 않으므로, 기존 위반은 허용하면서 새 위반만 막아야 한다.

## 검토한 선택지 (Options considered)
1. `FreezingArchRule` + `archunit_store`: 현재 위반을 파일로 저장해 두고, 그 외의 새 위반만 실패시킨다.
2. 테스트 코드 안에 규칙별 예외 목록(`Set<String>`)을 상수로 둔다. 항목마다 이유와 후속 조치를 주석으로 남긴다.
3. 위반이 있는 패키지를 검사 대상에서 통째로 뺀다.

## 결정 (Decision)
2번. 규칙(L1~L5, C1~C11)마다 테스트 메서드를 하나씩 두고, 기존 위반은 그 규칙의 예외 상수에 클래스 FQCN(C7은 `FQCN#메서드`)으로 적는다. `because(...)`에 `CLAUDE.md` 근거를 적어서 실패 메시지만 봐도 규칙의 이유가 보이게 한다.

## 이유 (Rationale)
- **리뷰에서 보인다.** 동결 store는 ArchUnit이 자동으로 생성하고 갱신하는 위반 문자열 파일이라 diff에서 무엇을 왜 허용했는지 읽기 어렵다. 예외 상수는 코드라서 항목 하나를 추가하면 그게 리뷰 대상이 되고, 이유 주석이 없으면 리뷰에서 걸러진다.
- **사람이 고쳐야만 바뀐다.** 동결 store는 갱신 여부가 ArchUnit 설정(`freeze.store.default.allowStoreUpdate` 등)에 달려 있고, 위반 메시지(메서드 시그니처 등)가 바뀌면 같은 위반이 새 위반으로 보인다. 예외 목록은 사람이 직접 고쳐야만 바뀐다.
- **위반 수가 적다.** 기존 위반은 규칙 6개(L3, L4, C4, C7, C9, C10)에 예외 항목 11개(클래스 8개, 메서드 3개)였다. 이 정도면 손으로 관리하는 비용이 동결 인프라보다 작다.
- **후속 작업 목록이 된다.** 각 예외 항목의 주석에 후속 조치를 적어 두었으므로, 고치는 이슈를 만들 때 이 목록을 그대로 쓰면 된다. 고친 뒤 항목을 지우면 그때부터 규칙이 그 클래스도 검사한다.
- 3번은 위반 클래스 하나 때문에 같은 패키지의 다른 클래스까지 검사에서 빠지므로 기각.

## 규칙으로 만들지 않은 것
- **기능 간 의존**(예: `service.feed`가 `service.team`을 사용): 기능끼리 협력하는 것은 정상이고, `CLAUDE.md`에도 금지 규칙이 없다. 규칙으로 만들면 예외 목록이 대부분의 service를 덮게 된다. domain slice 사이 순환(L5)만 막는다.
- **PatchField 사용 강제**(PATCH DTO는 `PatchField<T>`를 써야 한다): "설정 안 함"과 "명시적 null"을 구분해야 하는 필드인지는 요구사항에 달려 있어 코드 구조만으로 판정할 수 없다. 대신 `PatchField`를 쓴 필드에 deserializer가 붙어 있는지(C6)는 기계적으로 판정할 수 있어 규칙으로 만들었다.
- **`LocalDateTime.now()` 금지**(`global/time`의 `KstDateProvider` 사용 강제): `CLAUDE.md`에 적힌 규칙이 아니고, 프로덕션 코드에 직접 호출이 수십 곳 있어 예외 목록이 규칙 자체보다 커진다. 규칙으로 정할지부터 따로 결정해야 한다.
- **`OAuthAuthenticationException`(24건)**: `service.auth.exception`의 `RuntimeException` 서브클래스로, OAuth 실패를 리다이렉트 응답으로 바꾸는 별도 흐름에서 쓴다. C9는 `IllegalArgumentException`, `IllegalStateException`, `RuntimeException`, `UnsupportedOperationException`을 직접 `new` 하는 것만 막는다. 서브클래스 생성자의 `super(...)` 호출은 생성으로 보지 않는다. OAuth 예외를 `BusinessException`으로 통합할지는 응답 흐름 설계와 함께 따로 결정한다.

## 트레이드오프 / 남은 리스크
- 예외는 클래스 단위라서, 예외 목록에 있는 클래스에 같은 규칙의 위반이 새로 생겨도 잡지 못한다. 후속 이슈에서 위반을 고치고 항목을 지우는 것을 전제로 한다.
- 예외 판정은 중첩 클래스(Lombok builder, 익명 클래스 등)에도 바깥 클래스의 예외 여부를 그대로 적용한다. 예외 클래스 안에 새 중첩 클래스가 생기면 그 클래스도 검사 없이 예외가 된다.
- C11은 ArchUnit `GeneralCodingRules`를 그대로 쓴다. 이 라이브러리 규칙은 `java.util.logging` 타입의 필드를 설정하는 경우(`private static final Logger log = Logger.getLogger(...)`)를 잡고, 지역 변수로만 쓰는 호출은 잡지 않는다.
- ArchUnit은 테스트 클래스를 import하지 않는다(`ImportOption.Predefined.DO_NOT_INCLUDE_TESTS`). 테스트 코드는 규칙 대상이 아니다.
