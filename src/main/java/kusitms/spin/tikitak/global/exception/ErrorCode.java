package kusitms.spin.tikitak.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // Common
    COMMON001("COMMON001", "잘못된 요청입니다.", 400),
    COMMON002("COMMON002", "필수 파라미터가 누락되었습니다.", 400),
    COMMON003("COMMON003", "지원하지 않는 형식입니다.", 415),

    // System
    SYSTEM001("SYSTEM001", "서버 상태 확인에 실패했습니다.", 500),

    // Auth
    AUTH001("AUTH001", "인증이 필요합니다.", 401),
    AUTH002("AUTH002", "유효하지 않은 토큰입니다.", 401),
    AUTH003("AUTH003", "토큰이 만료되었습니다.", 401);

    private final String code;
    private final String message;
    private final int status;
}