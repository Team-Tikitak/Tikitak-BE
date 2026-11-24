package kusitms.spin.tikitak.service.notification.push;

public enum PushStatus {
	SUCCESS,
	/** 더 이상 유효하지 않은 토큰. 호출부가 토큰을 삭제한다. */
	INVALID_TOKEN,
	FAILED
}
