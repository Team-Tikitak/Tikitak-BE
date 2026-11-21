package kusitms.spin.tikitak.repository.notification;

import kusitms.spin.tikitak.domain.member.entity.Member;
import kusitms.spin.tikitak.domain.notification.entity.Notification;
import kusitms.spin.tikitak.domain.notification.enums.NotificationType;
import kusitms.spin.tikitak.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link NotificationRepositoryCustom}의 범위 술어(memberId, teamId), 정렬·limit, 커서, count, 일괄 읽음 검증.
 */
class NotificationQueryIntegrationTest extends IntegrationTest {

	private static final Long TEAM_A = 101L;
	private static final Long TEAM_B = 202L;
	private static final int LIMIT = 20;
	private static final LocalDateTime PREVIOUS_READ_AT = BASE_TIME.minusDays(1);
	private static final LocalDateTime READ_AT = BASE_TIME.plusDays(1);

	@Autowired
	private NotificationRepository notificationRepository;

	/**
	 * 공통 시나리오.
	 * <pre>
	 * n1: 나, 팀 A,    안 읽음  t+1
	 * n2: 나, 팀 B,    안 읽음  t+2
	 * n3: 나, 팀 없음, 안 읽음  t+3
	 * n4: 나, 팀 A,    읽음(readAt = PREVIOUS_READ_AT)  t+4
	 * n5: 나, 팀 A,    안 읽음  t+5
	 * other:     다른 회원, 팀 A, 안 읽음  t+6
	 * otherRead: 다른 회원, 팀 A, 읽음(readAt = PREVIOUS_READ_AT)  t+7
	 * </pre>
	 */
	private Scenario scenario() {
		Member me = persist(member("nq-me"));
		Member otherMember = persist(member("nq-other"));

		Notification n1 = persistWithCreatedAt(notification(me, TEAM_A, null), BASE_TIME.plusHours(1));
		Notification n2 = persistWithCreatedAt(notification(me, TEAM_B, null), BASE_TIME.plusHours(2));
		Notification n3 = persistWithCreatedAt(notification(me, null, null), BASE_TIME.plusHours(3));
		Notification n4 = persistWithCreatedAt(notification(me, TEAM_A, PREVIOUS_READ_AT), BASE_TIME.plusHours(4));
		Notification n5 = persistWithCreatedAt(notification(me, TEAM_A, null), BASE_TIME.plusHours(5));
		Notification other = persistWithCreatedAt(notification(otherMember, TEAM_A, null), BASE_TIME.plusHours(6));
		Notification otherRead = persistWithCreatedAt(
				notification(otherMember, TEAM_A, PREVIOUS_READ_AT), BASE_TIME.plusHours(7));

		flushAndClear();
		return new Scenario(me.getId(), otherMember.getId(),
				n1.getId(), n2.getId(), n3.getId(), n4.getId(), n5.getId(), other.getId(), otherRead.getId());
	}

	@Nested
	@DisplayName("목록")
	class FindPage {

		@Test
		@DisplayName("teamId를 지정하면 그 회원의 그 팀 알림만 최신순으로 반환한다")
		void teamIdGiven() {
			Scenario s = scenario();

			List<Notification> result = notificationRepository.findPage(
					new NotificationListCondition(s.me(), TEAM_A, null, null), LIMIT);

			assertThat(ids(result))
					.containsExactly(s.n5(), s.n4(), s.n1())
					.doesNotContain(s.n2(), s.n3(), s.other(), s.otherRead());
		}

		@Test
		@DisplayName("teamId가 null이면 그 회원의 모든 알림(teamId가 null인 알림 포함)을 최신순으로 반환한다")
		void teamIdNull() {
			Scenario s = scenario();

			List<Notification> result = notificationRepository.findPage(
					new NotificationListCondition(s.me(), null, null, null), LIMIT);

			assertThat(ids(result))
					.containsExactly(s.n5(), s.n4(), s.n3(), s.n2(), s.n1())
					.doesNotContain(s.other(), s.otherRead());
		}

		@Test
		@DisplayName("limit만큼만 반환한다")
		void respectsLimit() {
			Scenario s = scenario();

			List<Notification> result = notificationRepository.findPage(
					new NotificationListCondition(s.me(), null, null, null), 2);

			assertThat(ids(result)).containsExactly(s.n5(), s.n4());
		}
	}

	@Nested
	@DisplayName("커서")
	class Cursor {

		@Test
		@DisplayName("커서보다 이전 알림만 반환한다(teamId 지정)")
		void returnsNotificationsBeforeCursor() {
			Scenario s = scenario();
			Notification cursor = entityManager().find(Notification.class, s.n4());

			List<Notification> result = notificationRepository.findPage(
					new NotificationListCondition(s.me(), TEAM_A, cursor.getCreatedAt(), cursor.getId()), LIMIT);

			assertThat(ids(result)).containsExactly(s.n1());
		}

		@Test
		@DisplayName("createdAt이 같으면 id가 작은 알림만 다음 페이지에 온다")
		void sameCreatedAtUsesIdTieBreaker() {
			Member me = persist(member("nq-tie-me"));
			Member otherMember = persist(member("nq-tie-other"));

			LocalDateTime same = BASE_TIME;
			// persist 순서 = id 오름차순. createdAt은 명시적으로 덮어쓴다.
			// newerSmallId: id는 커서보다 작지만 createdAt이 더 늦어 제외돼야 한다.
			Notification newerSmallId = persistWithCreatedAt(notification(me, TEAM_A, null), same.plusHours(1));
			Notification sameFirst = persistWithCreatedAt(notification(me, TEAM_A, null), same);
			// otherTeamSame: createdAt·id 조건은 맞지만 팀 B라 teamId 지정 시 제외돼야 한다.
			Notification otherTeamSame = persistWithCreatedAt(notification(me, TEAM_B, null), same);
			// otherMemberSame: createdAt·id 조건은 맞지만 다른 회원이라 항상 제외돼야 한다.
			Notification otherMemberSame = persistWithCreatedAt(notification(otherMember, TEAM_A, null), same);
			Notification cursor = persistWithCreatedAt(notification(me, TEAM_A, null), same);
			Notification sameAfterCursor = persistWithCreatedAt(notification(me, TEAM_A, null), same);
			// olderLargeId: id는 커서보다 크지만 createdAt이 더 이르므로 포함돼야 한다.
			Notification olderLargeId = persistWithCreatedAt(notification(me, TEAM_A, null), same.minusHours(1));
			flushAndClear();

			assertThat(sameFirst.getCreatedAt()).isEqualTo(same);
			assertThat(cursor.getCreatedAt()).isEqualTo(same);
			assertThat(sameAfterCursor.getCreatedAt()).isEqualTo(same);
			assertThat(newerSmallId.getId()).isLessThan(cursor.getId());
			assertThat(sameFirst.getId()).isLessThan(cursor.getId());
			assertThat(sameAfterCursor.getId()).isGreaterThan(cursor.getId());
			assertThat(olderLargeId.getId()).isGreaterThan(cursor.getId());

			List<Notification> teamResult = notificationRepository.findPage(
					new NotificationListCondition(me.getId(), TEAM_A, same, cursor.getId()), LIMIT);
			assertThat(ids(teamResult))
					.containsExactly(sameFirst.getId(), olderLargeId.getId())
					.doesNotContain(newerSmallId.getId(), otherTeamSame.getId(), otherMemberSame.getId(),
							cursor.getId(), sameAfterCursor.getId());

			List<Notification> allTeamsResult = notificationRepository.findPage(
					new NotificationListCondition(me.getId(), null, same, cursor.getId()), LIMIT);
			assertThat(ids(allTeamsResult))
					.containsExactly(otherTeamSame.getId(), sameFirst.getId(), olderLargeId.getId())
					.doesNotContain(newerSmallId.getId(), otherMemberSame.getId(),
							cursor.getId(), sameAfterCursor.getId());
		}
	}

	@Nested
	@DisplayName("개수")
	class Count {

		@Test
		@DisplayName("countAll은 teamId를 지정하면 그 팀 알림 수, null이면 그 회원의 전체 알림 수이고 다른 회원 알림은 세지 않는다")
		void countAll() {
			Scenario s = scenario();

			assertThat(notificationRepository.countAll(new NotificationScope(s.me(), TEAM_A))).isEqualTo(3L);
			assertThat(notificationRepository.countAll(new NotificationScope(s.me(), TEAM_B))).isEqualTo(1L);
			assertThat(notificationRepository.countAll(new NotificationScope(s.me(), null))).isEqualTo(5L);
			assertThat(notificationRepository.countAll(new NotificationScope(s.otherMember(), null))).isEqualTo(2L);
		}

		@Test
		@DisplayName("countUnread는 읽은 알림과 다른 회원 알림을 세지 않는다(teamId 지정·null)")
		void countUnread() {
			Scenario s = scenario();

			assertThat(notificationRepository.countUnread(new NotificationScope(s.me(), TEAM_A))).isEqualTo(2L);
			assertThat(notificationRepository.countUnread(new NotificationScope(s.me(), TEAM_B))).isEqualTo(1L);
			assertThat(notificationRepository.countUnread(new NotificationScope(s.me(), null))).isEqualTo(4L);
			assertThat(notificationRepository.countUnread(new NotificationScope(s.otherMember(), null)))
					.isEqualTo(1L);
		}

		@Test
		@DisplayName("알림이 없으면 0을 반환한다")
		void zeroWhenEmpty() {
			Member lonely = persist(member("nq-lonely"));
			flushAndClear();

			assertThat(notificationRepository.countAll(new NotificationScope(lonely.getId(), null))).isZero();
			assertThat(notificationRepository.countUnread(new NotificationScope(lonely.getId(), TEAM_A))).isZero();
		}
	}

	@Nested
	@DisplayName("일괄 읽음")
	class MarkAllAsRead {

		@Test
		@DisplayName("teamId를 지정하면 그 회원의 그 팀 안 읽은 알림만 읽음 처리하고, 나머지와 기존 readAt은 그대로 둔다")
		void teamIdGiven() {
			Scenario s = scenario();

			long updated = notificationRepository.markAllAsRead(new NotificationScope(s.me(), TEAM_A), READ_AT);
			flushAndClear();

			assertThat(updated).isEqualTo(2L);
			assertRead(s.n1(), READ_AT);
			assertRead(s.n5(), READ_AT);
			assertRead(s.n4(), PREVIOUS_READ_AT);
			assertUnread(s.n2());
			assertUnread(s.n3());
			assertUnread(s.other());
			assertRead(s.otherRead(), PREVIOUS_READ_AT);
		}

		@Test
		@DisplayName("teamId가 null이면 그 회원의 안 읽은 알림 전체를 읽음 처리하고, 다른 회원 알림과 기존 readAt은 그대로 둔다")
		void teamIdNull() {
			Scenario s = scenario();

			long updated = notificationRepository.markAllAsRead(new NotificationScope(s.me(), null), READ_AT);
			flushAndClear();

			assertThat(updated).isEqualTo(4L);
			assertRead(s.n1(), READ_AT);
			assertRead(s.n2(), READ_AT);
			assertRead(s.n3(), READ_AT);
			assertRead(s.n5(), READ_AT);
			assertRead(s.n4(), PREVIOUS_READ_AT);
			assertUnread(s.other());
			assertRead(s.otherRead(), PREVIOUS_READ_AT);
		}

		@Test
		@DisplayName("대상이 없으면 0을 반환하고 기존 readAt을 바꾸지 않는다")
		void noTarget() {
			Scenario s = scenario();
			notificationRepository.markAllAsRead(new NotificationScope(s.me(), null), READ_AT);
			flushAndClear();

			long updated = notificationRepository.markAllAsRead(
					new NotificationScope(s.me(), null), READ_AT.plusDays(1));
			flushAndClear();

			assertThat(updated).isZero();
			assertRead(s.n1(), READ_AT);
		}
	}

	// --- helpers ---

	private record Scenario(
			Long me, Long otherMember,
			Long n1, Long n2, Long n3, Long n4, Long n5, Long other, Long otherRead
	) {
	}

	private static List<Long> ids(List<Notification> notifications) {
		return notifications.stream().map(Notification::getId).toList();
	}

	private void assertRead(Long id, LocalDateTime expectedReadAt) {
		Notification found = entityManager().find(Notification.class, id);
		assertThat(found.isRead()).as("isRead of %d", id).isTrue();
		assertThat(found.getReadAt()).as("readAt of %d", id).isEqualTo(expectedReadAt);
	}

	private void assertUnread(Long id) {
		Notification found = entityManager().find(Notification.class, id);
		assertThat(found.isRead()).as("isRead of %d", id).isFalse();
		assertThat(found.getReadAt()).as("readAt of %d", id).isNull();
	}

	/**
	 * readAt이 null이면 안 읽은 알림, non-null이면 그 시각에 읽은 알림을 만든다.
	 */
	private Notification notification(Member member, Long teamId, LocalDateTime readAt) {
		return Notification.builder()
				.member(member)
				.type(NotificationType.FEED_COMMENT)
				.title("알림")
				.body("알림 본문")
				.teamId(teamId)
				.isRead(readAt != null)
				.readAt(readAt)
				.createdAt(BASE_TIME)
				.build();
	}
}
