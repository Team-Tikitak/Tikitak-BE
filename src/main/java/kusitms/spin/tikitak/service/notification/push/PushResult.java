package kusitms.spin.tikitak.service.notification.push;

/**
 * 토큰 하나의 발송 결과. 토큰 원문은 담지 않으며, 호출부가 입력 순서(인덱스)로 토큰과 짝짓는다.
 */
public record PushResult(PushStatus status, String errorCode, String errorMessage) {

	public static PushResult success() {
		return new PushResult(PushStatus.SUCCESS, null, null);
	}

	public static PushResult invalidToken(String errorCode, String errorMessage) {
		return new PushResult(PushStatus.INVALID_TOKEN, errorCode, errorMessage);
	}

	public static PushResult failed(String errorCode, String errorMessage) {
		return new PushResult(PushStatus.FAILED, errorCode, errorMessage);
	}
}
