package kusitms.spin.tikitak.service.notification.push;

import java.util.List;

/**
 * 푸시 알림 발송기. {@code NotificationService}는 이 인터페이스에만 의존하고, 발송 SDK 타입은 구현체 밖으로 나오지 않는다.
 */
public interface PushSender {

	/**
	 * 발송할 수 있는 상태인지 돌려준다. false면 호출부는 토큰을 조회하지 않고 {@link #send}도 부르지 않는다.
	 */
	boolean isAvailable();

	/**
	 * 토큰마다 같은 메시지를 보낸다.
	 *
	 * @return 입력 {@code tokens}와 크기·순서가 같은 결과 목록
	 */
	List<PushResult> send(List<String> tokens, PushMessage message);
}
