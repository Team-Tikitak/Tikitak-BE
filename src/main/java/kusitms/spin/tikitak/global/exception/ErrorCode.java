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
    AUTH003("AUTH003", "토큰이 만료되었습니다.", 401),
    AUTH006("AUTH006", "리프레시 토큰이 필요합니다.", 400),
    AUTH007("AUTH007", "유효하지 않은 리프레시 토큰입니다.", 401),
    AUTH008("AUTH008", "토큰 재발급에 실패했습니다.", 500),
    AUTH009("AUTH009", "인증이 필요합니다.", 401),
    AUTH101("AUTH101", "지원하지 않는 OAuth Provider입니다.", 400),
    AUTH102("AUTH102", "OAuth 로그인 시작에 실패했습니다.", 500),
    AUTH103("AUTH103", "유효하지 않은 OAuth 인증 요청입니다.", 400),
    AUTH104("AUTH104", "OAuth 인증에 실패했습니다.", 401),
    AUTH105("AUTH105", "OAuth 콜백 처리에 실패했습니다.", 500),

    // Me
    ME009("ME009", "팀장은 팀이 존재하는 동안 탈퇴할 수 없습니다.", 400),
    ME010("ME010", "회원탈퇴 처리에 실패했습니다.", 500);

    private final String code;
    private final String message;
    private final int status;
}
