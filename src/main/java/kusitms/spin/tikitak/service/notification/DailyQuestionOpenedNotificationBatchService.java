package kusitms.spin.tikitak.service.notification;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import kusitms.spin.tikitak.domain.member.enums.MemberStatus;
import kusitms.spin.tikitak.domain.notification.dto.NotificationPayload;
import kusitms.spin.tikitak.domain.notification.enums.NotificationType;
import kusitms.spin.tikitak.domain.team.enums.TeamMemberStatus;
import kusitms.spin.tikitak.domain.team.enums.TeamStatus;
import kusitms.spin.tikitak.global.config.DailyQuestionOpenedNotificationProperties;
import kusitms.spin.tikitak.global.time.KstDateProvider;
import kusitms.spin.tikitak.repository.team.TeamMemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * 활성 팀에 활성 멤버십이 있는 활성 회원 전체에게 오늘의 질문 공개 알림을 회원당 한 번 보낸다.
 * 대량 발송 개선의 기준선이므로 최적화 없이 {@link NotificationService#send}를 차례로 부른다.
 * 트랜잭션을 걸지 않는다(send의 NOT_SUPPORTED가 바깥 트랜잭션을 중단만 해서 배치 내내 커넥션을 잡게 됨).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DailyQuestionOpenedNotificationBatchService {

	public static final String BATCH_DURATION_METRIC = "notification.batch.duration";
	public static final String RECIPIENT_QUERY_DURATION_METRIC = "notification.batch.recipient.query.duration";
	public static final String SEND_DURATION_METRIC = "notification.batch.send.duration";
	public static final String RECIPIENTS_METRIC = "notification.batch.recipients";
	public static final String SENDS_METRIC = "notification.batch.sends";

	private static final String TITLE = "오늘의 질문이 도착했어요";
	private static final String BODY = "팀원들과 오늘의 질문에 답해 보세요.";

	private static final NotificationType TYPE = NotificationType.DAILY_QUESTION_OPENED;
	private static final String TYPE_TAG = "type";
	private static final String RESULT_TAG = "result";

	private final TeamMemberRepository teamMemberRepository;
	private final NotificationService notificationService;
	private final MeterRegistry meterRegistry;

	@Scheduled(
			cron = "#{@'" + DailyQuestionOpenedNotificationProperties.BEAN_NAME + "'.cron()}",
			zone = KstDateProvider.KST_ZONE_ID
	)
	public void sendDailyQuestionOpenedNotifications() {
		Timer.Sample batchSample = Timer.start(meterRegistry);
		Timer.Sample querySample = Timer.start(meterRegistry);
		List<Long> recipientMemberIds = teamMemberRepository.findDistinctMemberIdsWithActiveMembership(
				MemberStatus.ACTIVE,
				TeamMemberStatus.ACTIVE,
				TeamStatus.ACTIVE
		);
		querySample.stop(timer(RECIPIENT_QUERY_DURATION_METRIC));

		int recipientCount = recipientMemberIds.size();
		DistributionSummary.builder(RECIPIENTS_METRIC)
				.tag(TYPE_TAG, TYPE.name())
				.register(meterRegistry)
				.record(recipientCount);
		log.info("Daily question opened notification batch started. recipientCount={}", recipientCount);

		Counter successCounter = sendCounter("success");
		Counter failureCounter = sendCounter("failure");
		NotificationPayload payload = payload();
		int successCount = 0;
		int failureCount = 0;

		Timer.Sample sendSample = Timer.start(meterRegistry);
		for (Long memberId : recipientMemberIds) {
			try {
				notificationService.send(memberId, payload);
				successCount++;
				successCounter.increment();
			} catch (Exception e) {
				failureCount++;
				failureCounter.increment();
				log.error("Failed to send daily question opened notification. memberId={}", memberId, e);
			}
		}
		sendSample.stop(timer(SEND_DURATION_METRIC));
		long elapsedNanos = batchSample.stop(timer(BATCH_DURATION_METRIC));

		log.info("Daily question opened notification batch completed. recipientCount={}, successCount={}, failureCount={}, elapsedMs={}",
				recipientCount, successCount, failureCount, Duration.ofNanos(elapsedNanos).toMillis());
	}

	private NotificationPayload payload() {
		return NotificationPayload.builder()
				.type(TYPE)
				.title(TITLE)
				.body(BODY)
				.build();
	}

	private Timer timer(String name) {
		return Timer.builder(name)
				.tag(TYPE_TAG, TYPE.name())
				.register(meterRegistry);
	}

	private Counter sendCounter(String result) {
		return Counter.builder(SENDS_METRIC)
				.tag(TYPE_TAG, TYPE.name())
				.tag(RESULT_TAG, result)
				.register(meterRegistry);
	}
}
