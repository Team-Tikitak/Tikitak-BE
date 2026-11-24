package kusitms.spin.tikitak.service.notification;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import kusitms.spin.tikitak.domain.member.enums.MemberStatus;
import kusitms.spin.tikitak.domain.notification.dto.NotificationPayload;
import kusitms.spin.tikitak.domain.notification.enums.NotificationType;
import kusitms.spin.tikitak.domain.team.enums.TeamMemberStatus;
import kusitms.spin.tikitak.domain.team.enums.TeamStatus;
import kusitms.spin.tikitak.global.time.KstDateProvider;
import kusitms.spin.tikitak.repository.team.TeamMemberRepository;
import kusitms.spin.tikitak.support.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class DailyQuestionOpenedNotificationBatchServiceTest extends UnitTest {

	private static final String TYPE = "DAILY_QUESTION_OPENED";

	@Mock
	private TeamMemberRepository teamMemberRepository;

	@Mock
	private NotificationService notificationService;

	private SimpleMeterRegistry meterRegistry;

	private DailyQuestionOpenedNotificationBatchService batchService;

	@BeforeEach
	void setUp() {
		meterRegistry = new SimpleMeterRegistry();
		batchService = new DailyQuestionOpenedNotificationBatchService(
				teamMemberRepository,
				notificationService,
				meterRegistry
		);
	}

	@Test
	@DisplayName("받는 사람마다 공통 문구의 DAILY_QUESTION_OPENED 알림을 한 번씩 보낸다")
	void sendsCommonPayloadToEachRecipientOnce() {
		givenRecipients(List.of(1L, 2L, 3L));

		batchService.sendDailyQuestionOpenedNotifications();

		ArgumentCaptor<NotificationPayload> payloadCaptor = ArgumentCaptor.forClass(NotificationPayload.class);
		InOrder inOrder = inOrder(notificationService);
		inOrder.verify(notificationService).send(eq(1L), payloadCaptor.capture());
		inOrder.verify(notificationService).send(eq(2L), payloadCaptor.capture());
		inOrder.verify(notificationService).send(eq(3L), payloadCaptor.capture());
		verifyNoMoreInteractions(notificationService);

		NotificationPayload expected = NotificationPayload.builder()
				.type(NotificationType.DAILY_QUESTION_OPENED)
				.title("오늘의 질문이 도착했어요")
				.body("팀원들과 오늘의 질문에 답해 보세요.")
				.actorTeamMemberId(null)
				.data(Map.of())
				.build();
		// NotificationPayload에 equals가 없어 필드 전체를 재귀 비교한다.
		assertThat(payloadCaptor.getAllValues())
				.hasSize(3)
				.allSatisfy(payload -> assertThat(payload).usingRecursiveComparison().isEqualTo(expected));
		assertThat(sendCount("success")).isEqualTo(3.0);
		assertThat(sendCount("failure")).isEqualTo(0.0);
	}

	@Test
	@DisplayName("받는 사람은 활성 회원, 활성 멤버십, 활성 팀 조건으로 조회한다")
	void queriesRecipientsWithActiveStatuses() {
		givenRecipients(List.of());

		batchService.sendDailyQuestionOpenedNotifications();

		verify(teamMemberRepository, times(1)).findDistinctMemberIdsWithActiveMembership(
				MemberStatus.ACTIVE,
				TeamMemberStatus.ACTIVE,
				TeamStatus.ACTIVE
		);
		verifyNoMoreInteractions(teamMemberRepository);
	}

	@Test
	@DisplayName("중간 회원의 send가 예외를 던져도 뒤 회원에게 계속 보내고 성공/실패를 따로 센다")
	void continuesAfterFailureAndCountsResults() {
		givenRecipients(List.of(1L, 2L, 3L));
		// strict stub의 인자 불일치 예외(PotentialStubbingProblem)가 배치의 catch에 삼켜지지 않도록 모든 호출을 한 stub으로 받는다.
		willAnswer(invocation -> {
			if (Long.valueOf(2L).equals(invocation.getArgument(0))) {
				throw new RuntimeException("boom");
			}
			return null;
		}).given(notificationService).send(any(), any());

		batchService.sendDailyQuestionOpenedNotifications();

		InOrder inOrder = inOrder(notificationService);
		inOrder.verify(notificationService).send(eq(1L), any(NotificationPayload.class));
		inOrder.verify(notificationService).send(eq(2L), any(NotificationPayload.class));
		inOrder.verify(notificationService).send(eq(3L), any(NotificationPayload.class));
		verifyNoMoreInteractions(notificationService);
		assertThat(sendCount("success")).isEqualTo(2.0);
		assertThat(sendCount("failure")).isEqualTo(1.0);
	}

	@Test
	@DisplayName("대상이 0명이면 send를 부르지 않고 대상 수 0을 기록한다")
	void recordsZeroRecipientsWithoutSending() {
		givenRecipients(List.of());

		batchService.sendDailyQuestionOpenedNotifications();

		verify(notificationService, never()).send(any(), any());
		DistributionSummary recipients = meterRegistry.get("notification.batch.recipients")
				.tag("type", TYPE)
				.summary();
		assertThat(recipients.count()).isEqualTo(1L);
		assertThat(recipients.totalAmount()).isEqualTo(0.0);
		assertThat(sendCount("success")).isEqualTo(0.0);
		assertThat(sendCount("failure")).isEqualTo(0.0);
	}

	@Test
	@DisplayName("실행당 대상 수를 summary에 기록한다")
	void recordsRecipientCount() {
		givenRecipients(List.of(1L, 2L));

		batchService.sendDailyQuestionOpenedNotifications();

		DistributionSummary recipients = meterRegistry.get("notification.batch.recipients")
				.tag("type", TYPE)
				.summary();
		assertThat(recipients.count()).isEqualTo(1L);
		assertThat(recipients.totalAmount()).isEqualTo(2.0);
	}

	@Test
	@DisplayName("배치, 대상 조회, 발송 루프 Timer를 type 태그와 함께 각 1회 기록한다")
	void recordsTimersOncePerRun() {
		givenRecipients(List.of(1L));

		batchService.sendDailyQuestionOpenedNotifications();

		assertThat(timer("notification.batch.duration").count()).isEqualTo(1L);
		assertThat(timer("notification.batch.recipient.query.duration").count()).isEqualTo(1L);
		assertThat(timer("notification.batch.send.duration").count()).isEqualTo(1L);
	}

	@Test
	@DisplayName("대상 조회가 예외를 던지면 발송·지표 없이 예외를 전파한다")
	void propagatesRecipientQueryFailureWithoutSendingOrMetrics() {
		given(teamMemberRepository.findDistinctMemberIdsWithActiveMembership(
				MemberStatus.ACTIVE,
				TeamMemberStatus.ACTIVE,
				TeamStatus.ACTIVE
		)).willThrow(new RuntimeException("db down"));

		assertThatThrownBy(() -> batchService.sendDailyQuestionOpenedNotifications())
				.isInstanceOf(RuntimeException.class)
				.hasMessage("db down");

		verify(notificationService, never()).send(any(), any());
		assertThat(meterRegistry.getMeters()).isEmpty();
	}

	@Test
	@DisplayName("Properties 빈의 cron으로 KST 기준 실행된다")
	void isScheduledWithPropertiesCronInKst() throws NoSuchMethodException {
		Scheduled scheduled = DailyQuestionOpenedNotificationBatchService.class
				.getMethod("sendDailyQuestionOpenedNotifications")
				.getAnnotation(Scheduled.class);

		assertThat(scheduled).isNotNull();
		assertThat(scheduled.cron()).isEqualTo(
				"#{@'notification.daily-question-opened-"
						+ "kusitms.spin.tikitak.global.config.DailyQuestionOpenedNotificationProperties'.cron()}"
		);
		assertThat(scheduled.zone()).isEqualTo("Asia/Seoul");
		assertThat(scheduled.zone()).isEqualTo(KstDateProvider.KST_ZONE_ID);
	}

	private void givenRecipients(List<Long> memberIds) {
		given(teamMemberRepository.findDistinctMemberIdsWithActiveMembership(
				MemberStatus.ACTIVE,
				TeamMemberStatus.ACTIVE,
				TeamStatus.ACTIVE
		)).willReturn(memberIds);
	}

	private double sendCount(String result) {
		Counter counter = meterRegistry.get("notification.batch.sends")
				.tag("type", TYPE)
				.tag("result", result)
				.counter();
		return counter.count();
	}

	private Timer timer(String name) {
		return meterRegistry.get(name)
				.tag("type", TYPE)
				.timer();
	}
}
