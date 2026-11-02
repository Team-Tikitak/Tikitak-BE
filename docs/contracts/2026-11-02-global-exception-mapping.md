# 작업 계약: GlobalExceptionHandler 예외 매핑 및 로그 레벨 보완

- **관련 이슈**: #164
- **브랜치**: `fix/#164-global-exception-mapping` (develop에서 분기)
- **상태**: 승인됨 (2026-11-02)

## 목표
- Spring MVC 표준 예외가 500이 아니라 알맞은 4xx와 `ErrorCode`로 응답한다.
- 서버 내부 오류는 전용 코드로 응답해서, 클라이언트가 입력 오류(`COMMON001`)와 구분할 수 있다.
- 4xx는 `warn`, 5xx는 `error`로 기록해서 에러 로그와 알림에 4xx가 섞이지 않는다.

## 매핑 표

| 예외 | 상태 | ErrorCode |
|---|---|---|
| `NoResourceFoundException`, `NoHandlerFoundException` | 404 | `COMMON005` (신규) "요청한 리소스를 찾을 수 없습니다." |
| `HttpRequestMethodNotSupportedException` | 405 + `Allow` 헤더 | `COMMON006` (신규) "지원하지 않는 HTTP 메서드입니다." |
| `MissingServletRequestParameterException`, `MissingRequestHeaderException` | 400 | `COMMON002` (기존) |
| `jakarta.validation.ConstraintViolationException`, `HandlerMethodValidationException` | 400 | `COMMON001` (기존), `errors`에 위반 항목 목록 |
| `HttpMediaTypeNotSupportedException` | 415 | `COMMON003` (기존, 현재 미사용) |
| 그 외 `Exception` | 500 | `COMMON004` (신규) "서버 내부 오류가 발생했습니다." |

로그 레벨은 다음과 같이 나눈다.
- `BusinessException`은 `errorCode.getStatus() >= 500`이면 `log.error(..., e)`로 스택트레이스까지 남기고, 그 외에는 `log.warn`으로 남긴다.
- 위 표의 4xx 핸들러와 기존 400 핸들러 3개(MethodArgumentNotValid, TypeMismatch, NotReadable)는 `log.warn`으로 남긴다.

## 범위: 건드려도 되는 파일
- `src/main/java/kusitms/spin/tikitak/global/exception/GlobalExceptionHandler.java`
- `src/main/java/kusitms/spin/tikitak/global/exception/ErrorCode.java` (COMMON 항목 추가만)
- `src/test/java/kusitms/spin/tikitak/global/exception/GlobalExceptionHandlerTest.java` (신규)
  - `extends ApiTest`로 만든다.
  - 테스트 전용 nested `@RestController`를 두고 `mockMvc(new TestController())`로 검증한다.

## 완료 조건
- [ ] `./gradlew test` 통과 (기존 `MediaControllerTest`의 400/`COMMON001` 단언 포함)
- [ ] `GlobalExceptionHandlerTest`가 매핑 표의 행마다 `status`, `$.code`, `$.success=false`를 단언한다.
  - 404는 standalone에서 실제로 나는 예외를 사용한다.
  - ConstraintViolation은 테스트 컨트롤러에서 직접 throw해도 된다.
- [ ] 405 응답에 `Allow` 헤더가 있다.
- [ ] 미분류 예외는 500 + `COMMON004`로 응답한다. `COMMON001`이 아니다.
- [ ] 5xx `BusinessException`은 4xx와 다른 로그 레벨로 기록한다.
  - 테스트 대상이 아니라 verifier가 코드를 읽어서 판정한다.

## 하지 말 것
- 범위 밖 리팩터링, 포맷 정리. 기존 핸들러의 응답 코드도 바꾸지 않는다.
- `ApiTest`, `ErrorResponse`, `SecurityConfig` 수정. `SecurityConfig`는 사용자 WIP로 미커밋 상태다.
- 서비스에서 던지는 `COMMON001`/`COMMON002`의 의미 변경
- `ResponseEntityExceptionHandler` 상속으로 구조 전환 (별도 판단 필요)
- 사람 승인 없이 커밋/푸시

## 범위 밖 (알고 넘어가는 것)
- 인증이 필요한 경로의 없는 URL은 Security 필터에서 401(AUTH009)이 먼저 난다. 이번 작업으로 바뀌지 않으며, ApiTest로 검증할 수도 없다.

## 참고
- researcher 조사(2026-11-02):
  - advice는 `GlobalExceptionHandler` 하나다.
  - 현재 미분류 예외는 500 + `COMMON001`으로 응답한다.
  - `@Validated` 컨트롤러는 `PlaceController` 하나다.
  - 운영 코드에 `@RequestHeader` 사용처는 없다. 방어적으로 추가한다.
