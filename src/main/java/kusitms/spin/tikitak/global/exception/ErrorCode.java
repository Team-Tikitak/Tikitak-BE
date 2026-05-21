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
    ME001("ME001", "회원 정보를 찾을 수 없습니다.", 404),
    ME002("ME002", "내 계정 정보 조회에 실패했습니다.", 500),
    ME003("ME003", "내 참여 팀 목록 조회에 실패했습니다.", 500),
    ME007("ME007", "활성 팀으로 변경할 수 없는 팀입니다.", 400),
    ME008("ME008", "활성 팀 변경에 실패했습니다.", 500),
    ME009("ME009", "팀장은 팀이 존재하는 동안 탈퇴할 수 없습니다.", 400),
    ME010("ME010", "회원탈퇴 처리에 실패했습니다.", 500),
    ME011("ME011", "온보딩 프로필 저장에 실패했습니다.", 500),

    // Agreement
    AGREEMENT001("AGREEMENT001", "약관 동의 현황 조회에 실패했습니다.", 500),
    AGREEMENT002("AGREEMENT002", "필수 약관에 동의해야 합니다.", 400),
    AGREEMENT003("AGREEMENT003", "약관 동의 저장에 실패했습니다.", 500),

    // Media
    MEDIA001("MEDIA001", "지원하지 않는 이미지 형식입니다.", 400),
    MEDIA002("MEDIA002", "이미지 용량은 1장당 10MB를 초과할 수 없습니다.", 400),
    MEDIA003("MEDIA003", "업로드 목적이 올바르지 않습니다.", 400),
    MEDIA004("MEDIA004", "업로드할 파일이 필요합니다.", 400),
    MEDIA005("MEDIA005", "업로드 가능한 이미지 개수를 초과했습니다.", 400),
    MEDIA006("MEDIA006", "업로드 URL 발급에 실패했습니다.", 500),
    MEDIA007("MEDIA007", "이미 사용 중인 미디어는 삭제할 수 없습니다.", 400),
    MEDIA008("MEDIA008", "해당 미디어를 삭제할 수 없습니다.", 403),
    MEDIA009("MEDIA009", "미디어를 찾을 수 없습니다.", 404),
    MEDIA010("MEDIA010", "미디어 삭제에 실패했습니다.", 500),
    MEDIA011("MEDIA011", "업로드 완료 처리할 미디어가 필요합니다.", 400),
    MEDIA012("MEDIA012", "업로드가 완료되지 않은 미디어입니다.", 400),
    MEDIA013("MEDIA013", "업로드된 파일 정보가 일치하지 않습니다.", 400),
    MEDIA014("MEDIA014", "이미 사용 중인 미디어는 업로드 완료 처리할 수 없습니다.", 400),
    MEDIA015("MEDIA015", "해당 업로드 요청에 접근할 수 없습니다.", 403),
    MEDIA016("MEDIA016", "업로드 요청을 찾을 수 없습니다.", 404),
    MEDIA017("MEDIA017", "업로드 완료 처리에 실패했습니다.", 500),
    MEDIA018("MEDIA018", "사용할 수 없는 미디어입니다.", 400),

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
    TEAM_MEMBER003("TEAM_MEMBER003", "해당 팀 멤버가 아닙니다.", 400),
    TEAM_MEMBER004("TEAM_MEMBER004", "강퇴할 권한이 없습니다", 403),
    TEAM_MEMBER005("TEAM_MEMBER005", "팀장은 강퇴할 수 없습니다", 400),

    // Place
    PLACE001("PLACE001", "장소 검색에 실패했습니다.", 500),
    PLACE002("PLACE002", "검색어를 입력해주세요.", 400),

    // Invite
    INVITE001("INVITE001", "초대 링크를 생성할 권한이 없습니다.", 403),
    INVITE002("INVITE002", "활성화된 초대 링크가 없습니다.", 404),
    INVITE003("INVITE003", "초대 링크를 조회할 권한이 없습니다.", 403),
    INVITE004("INVITE004", "유효하지 않은 초대 링크입니다.", 400),
    INVITE005("INVITE005", "만료된 초대 링크입니다.", 400),
    INVITE006("INVITE006", "이미 팀에 참여 중인 멤버입니다.", 400),
    INVITE007("INVITE007", "강퇴된 멤버는 초대 링크로 참여할 수 없습니다.", 403),

    // Feed
    FEED001("FEED001", "유효하지 않은 피드 유형입니다.", 400),
    FEED002("FEED002", "피드 목록 조회에 실패했습니다.", 500),
    FEED003("FEED003", "피드를 찾을 수 없습니다.", 404),
    FEED004("FEED004", "피드 상세 조회에 실패했습니다.", 500),
    FEED005("FEED005", "피드 이미지는 최소 1장 이상 필요합니다.", 400),
    FEED006("FEED006", "피드 이미지는 최대 10장까지 등록할 수 있습니다.", 400),
    FEED007("FEED007", "본문 형식이 올바르지 않습니다.", 400),
    FEED008("FEED008", "태그할 수 없는 팀 멤버가 포함되어 있습니다.", 400),
    FEED009("FEED009", "팀원 태그는 최대 11명까지 가능합니다.", 400),
    FEED010("FEED010", "피드 작성에 실패했습니다.", 500),
    FEED011("FEED011", "해당 피드를 수정할 수 없습니다.", 403),
    FEED012("FEED012", "피드 수정에 실패했습니다.", 500),
    FEED013("FEED013", "유효하지 않은 반응 유형입니다.", 400),
    FEED014("FEED014", "피드 반응 처리에 실패했습니다.", 500),
    FEED015("FEED015", "해당 피드를 삭제할 수 없습니다.", 403),
    FEED016("FEED016", "피드 삭제에 실패했습니다.", 500),

    // Daily Question
    DAILY_QUESTION001("DAILY_QUESTION001", "오늘의 질문을 찾을 수 없습니다.", 404),
    DAILY_QUESTION002("DAILY_QUESTION002", "오늘의 질문이 아닙니다.", 400),
    DAILY_QUESTION003("DAILY_QUESTION003", "이미 오늘의 질문에 답변했습니다.", 409),
    DAILY_QUESTION004("DAILY_QUESTION004", "오늘의 질문 답변을 찾을 수 없습니다.", 404),
    DAILY_QUESTION005("DAILY_QUESTION005", "수정할 답변 정보가 없습니다.", 400),
    DAILY_QUESTION006("DAILY_QUESTION006", "오늘의 질문 답변 이미지는 필수입니다.", 400),
    DAILY_QUESTION007("DAILY_QUESTION007", "사용할 수 없는 오늘의 질문 답변 이미지입니다.", 400),
    DAILY_QUESTION008("DAILY_QUESTION008", "오늘의 질문 답변은 전용 API로만 수정할 수 있습니다.", 400),
    DAILY_QUESTION009("DAILY_QUESTION009", "오늘의 질문 답변 본문 형식이 올바르지 않습니다.", 400),

    // Comment
    COMMENT001("COMMENT001", "댓글 목록 조회에 실패했습니다.", 500),
    COMMENT002("COMMENT002", "댓글 내용은 필수입니다.", 400),
    COMMENT003("COMMENT003", "댓글은 최대 200자까지 입력할 수 있습니다.", 400),
    COMMENT004("COMMENT004", "댓글 작성에 실패했습니다.", 500),
    COMMENT005("COMMENT005", "해당 댓글을 삭제할 수 없습니다.", 403),
    COMMENT006("COMMENT006", "댓글을 찾을 수 없습니다.", 404),
    COMMENT007("COMMENT007", "댓글 삭제에 실패했습니다.", 500),
    COMMENT008("COMMENT008", "해당 댓글을 수정할 수 없습니다.", 403),
    COMMENT009("COMMENT009", "댓글 수정에 실패했습니다.", 500),
    COMMENT010("COMMENT010", "댓글 위치가 올바르지 않습니다.", 400),
    COMMENT011("COMMENT011", "댓글을 작성할 피드 이미지를 찾을 수 없습니다.", 404),
    COMMENT012("COMMENT012", "수정할 댓글 정보가 없습니다.", 400);

    private final String code;
    private final String message;
    private final int status;
}
