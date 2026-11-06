package kusitms.spin.tikitak.domain.common;

import kusitms.spin.tikitak.domain.auth.entity.RefreshToken;
import kusitms.spin.tikitak.domain.feed.entity.Feed;
import kusitms.spin.tikitak.domain.feed.entity.FeedComment;
import kusitms.spin.tikitak.domain.feed.entity.FeedImage;
import kusitms.spin.tikitak.domain.feed.entity.FeedTag;
import kusitms.spin.tikitak.domain.member.entity.Member;
import kusitms.spin.tikitak.domain.notification.dto.NotificationPayload;
import kusitms.spin.tikitak.domain.notification.entity.Notification;
import kusitms.spin.tikitak.domain.notification.enums.NotificationType;
import kusitms.spin.tikitak.domain.team.entity.Team;
import kusitms.spin.tikitak.domain.team.entity.TeamMember;
import kusitms.spin.tikitak.domain.team.enums.TeamMemberRole;
import kusitms.spin.tikitak.domain.team.enums.TeamMemberStatus;
import kusitms.spin.tikitak.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 2단계(#183)에서 BaseTimeEntity/BaseCreatedTimeEntity로 옮긴 엔티티의 시각 동작을 검증한다.
 * 이전 시각과 비교할 때는 시계 정밀도에 기대지 않도록 저장된 updated_at을 native update로 1일 전으로 되돌린다.
 */
class BaseTimeEntityMigrationIntegrationTest extends IntegrationTest {

	@Test
	@DisplayName("Feed: 도메인 수정 메서드 직후(flush 전)에 updatedAt이 이전 값보다 늦다")
	void feedUpdateTouchesUpdatedAtBeforeFlush() {
		Feed feed = persistFeed("feed-touch");
		LocalDateTime backdated = backdateUpdatedAt("feed", feed.getId());

		Feed loaded = entityManager().find(Feed.class, feed.getId());
		assertThat(loaded.getUpdatedAt()).isEqualTo(backdated);

		loaded.updateGeneralFeed("수정된 내용", null);

		assertThat(loaded.getUpdatedAt()).isAfter(backdated);
	}

	@Test
	@DisplayName("Feed: 태그만 바꿔도(엔티티 자기 컬럼 변경 없음) flush 후 DB updated_at이 이전 값보다 늦다")
	void feedReplaceTagsUpdatesUpdatedAtInDb() {
		Feed feed = persistFeed("feed-tags");
		TeamMember tagged = persist(teamMember(
				persist(member("feed-tags-tagged")), feed.getTeam(), TeamMemberRole.MEMBER, TeamMemberStatus.ACTIVE));
		flushAndClear();
		LocalDateTime backdated = backdateUpdatedAt("feed", feed.getId());

		Feed loaded = entityManager().find(Feed.class, feed.getId());
		TeamMember taggedRef = entityManager().getReference(TeamMember.class, tagged.getId());
		loaded.replaceTags(List.of(FeedTag.builder().teamMember(taggedRef).build()));
		flushAndClear();

		Feed reloaded = entityManager().find(Feed.class, feed.getId());
		assertThat(reloaded.getTags()).hasSize(1);
		assertThat(reloaded.getContent()).isEqualTo("테스트 피드");
		assertThat(reloaded.getUpdatedAt()).isAfter(backdated);
	}

	@Test
	@DisplayName("FeedComment: updateContent 직후(flush 전)에 updatedAt이 이전 값보다 늦다")
	void feedCommentUpdateContentTouchesUpdatedAtBeforeFlush() {
		Feed feed = persistFeed("comment-touch");
		FeedImage image = FeedImage.builder().imgUrl("https://example.com/feed.png").orderIndex(0).build();
		feed.addImage(image);
		persist(image);
		FeedComment comment = persist(FeedComment.builder()
				.feed(feed)
				.feedImage(image)
				.teamMember(feed.getTeamMember())
				.content("댓글")
				.positionX(new BigDecimal("0.500000"))
				.positionY(new BigDecimal("0.500000"))
				.deleted(false)
				.build());
		flushAndClear();
		LocalDateTime backdated = backdateUpdatedAt("feed_comment", comment.getId());

		FeedComment loaded = entityManager().find(FeedComment.class, comment.getId());
		assertThat(loaded.getUpdatedAt()).isEqualTo(backdated);

		loaded.updateContent("수정된 댓글");

		assertThat(loaded.getUpdatedAt()).isAfter(backdated);
	}

	@Test
	@DisplayName("Member: 같은 값으로 updateSocialProfile하면 flush 후에도 updated_at이 그대로다")
	void memberSameValueUpdateKeepsUpdatedAt() {
		Member member = persist(member("member-same"));
		flushAndClear();
		LocalDateTime backdated = backdateUpdatedAt("member", member.getId());

		Member loaded = entityManager().find(Member.class, member.getId());
		loaded.updateSocialProfile(loaded.getEmail(), loaded.getName(), loaded.getProfileImgUrl());
		flushAndClear();

		Member reloaded = entityManager().find(Member.class, member.getId());
		assertThat(reloaded.getUpdatedAt()).isEqualTo(backdated);
	}

	@Test
	@DisplayName("Member: 다른 값으로 updateSocialProfile하면 flush 후 updated_at이 이전 값보다 늦다")
	void memberChangedValueUpdateAdvancesUpdatedAt() {
		Member member = persist(member("member-changed"));
		flushAndClear();
		LocalDateTime backdated = backdateUpdatedAt("member", member.getId());

		Member loaded = entityManager().find(Member.class, member.getId());
		loaded.updateSocialProfile("changed@example.com", loaded.getName(), loaded.getProfileImgUrl());
		flushAndClear();

		Member reloaded = entityManager().find(Member.class, member.getId());
		assertThat(reloaded.getEmail()).isEqualTo("changed@example.com");
		assertThat(reloaded.getUpdatedAt()).isAfter(backdated);
	}

	@Test
	@DisplayName("Notification: 팩토리로 만들어 persist하면 createdAt이 채워진다")
	void notificationFactoryFillsCreatedAtOnPersist() {
		Member member = persist(member("notification-created"));
		Notification notification = Notification.create(member, NotificationPayload.builder()
				.type(NotificationType.FEED_COMMENT)
				.title("title")
				.body("body")
				.build());
		assertThat(notification.getCreatedAt()).isNull();

		persist(notification);
		flushAndClear();

		Notification loaded = entityManager().find(Notification.class, notification.getId());
		assertThat(loaded.getCreatedAt()).isNotNull();
	}

	@Test
	@DisplayName("RefreshToken: 팩토리로 만들어 persist하면 createdAt이 채워진다")
	void refreshTokenFactoryFillsCreatedAtOnPersist() {
		Member member = persist(member("refresh-created"));
		RefreshToken token = RefreshToken.issue(member, "token-hash-refresh-created", LocalDateTime.now().plusDays(14));
		assertThat(token.getCreatedAt()).isNull();

		persist(token);
		flushAndClear();

		RefreshToken loaded = entityManager().find(RefreshToken.class, token.getId());
		assertThat(loaded.getCreatedAt()).isNotNull();
	}

	// --- helpers ---

	private Feed persistFeed(String suffix) {
		Member author = persist(member(suffix + "-author"));
		Team team = persist(team(suffix));
		TeamMember authorTm = persist(teamMember(author, team, TeamMemberRole.OWNER, TeamMemberStatus.ACTIVE));
		Feed feed = persist(Feed.builder()
				.team(team)
				.teamMember(authorTm)
				.content("테스트 피드")
				.build());
		flushAndClear();
		return entityManager().find(Feed.class, feed.getId());
	}

	/**
	 * 저장된 updated_at을 DB의 created_at보다 1일 이전 값으로 바꾸고 영속성 컨텍스트를 비운 뒤, 바꾼 값을 돌려준다.
	 * DB에서 읽은 값을 기준으로 하므로 DB 정밀도(µs)와 일치한다.
	 */
	private LocalDateTime backdateUpdatedAt(String table, Long id) {
		entityManager().flush();
		LocalDateTime createdAt = (LocalDateTime) entityManager()
				.createNativeQuery("SELECT created_at FROM " + table + " WHERE id = :id", LocalDateTime.class)
				.setParameter("id", id)
				.getSingleResult();
		LocalDateTime backdated = createdAt.minusDays(1);
		int updated = entityManager()
				.createNativeQuery("UPDATE " + table + " SET updated_at = :updatedAt WHERE id = :id")
				.setParameter("updatedAt", backdated)
				.setParameter("id", id)
				.executeUpdate();
		assertThat(updated).isEqualTo(1);
		flushAndClear();
		return backdated;
	}
}
