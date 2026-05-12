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
    ME010("ME010", "회원탈퇴 처리에 실패했습니다.", 500),

    // Media
    MEDIA001("MEDIA001", "지원하지 않는 이미지 형식입니다.", 400),
    MEDIA002("MEDIA002", "이미지 용량은 1장당 10MB를 초과할 수 없습니다.", 400),
    MEDIA003("MEDIA003", "업로드 목적이 올바르지 않습니다.", 400),
    MEDIA004("MEDIA004", "업로드할 파일이 필요합니다.", 400),
    MEDIA005("MEDIA005", "업로드 가능한 이미지 개수를 초과했습니다.", 400),
    MEDIA006("MEDIA006", "업로드 URL 발급에 실패했습니다.", 500),

    // Member
    MEMBER001("MEMBER001", "존재하지 않는 사용자입니다.", 404),

    // Team
    TEAM001("TEAM001", "존재하지 않는 팀입니다", 404),
    TEAM002("TEAM002", "조회할 권한이 없는 팀입니다", 403),
    TEAM003("TEAM003", "수정할 권한이 없는 팀입니다", 403),
    TEAM004("TEAM004", "이미 삭제 신청된 팀입니다", 400),
    TEAM005("TEAM005", "삭제할 권한이 없는 팀입니다", 403),
    TEAM006("TEAM006", "삭제 신청되지 않은 팀입니다", 400),
    TEAM007("TEAM007", "복구할 권한이 없는 팀입니다", 403),
    TEAM008("TEAM008", "해당 팀에 접근할 수 없습니다.", 403),
    TEAM009("TEAM009", "팀을 찾을 수 없습니다.", 404),

    // TeamMember
    TEAM_MEMBER001("TEAM_MEMBER001", "존재하지 않는 팀 멤버입니다", 404),
    TEAM_MEMBER002("TEAM_MEMBER002", "팀장은 팀이 존재하는 동안 나갈 수 없습니다", 400),
    TEAM_MEMBER003("TEAM_MEMBER003", "해당 팀 멤버가 아닙니다.", 400);

    private final String code;
    private final String message;
    private final int status;
}
