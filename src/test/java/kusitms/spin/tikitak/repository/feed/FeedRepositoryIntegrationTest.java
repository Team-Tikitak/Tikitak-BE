package kusitms.spin.tikitak.repository.feed;

import kusitms.spin.tikitak.domain.feed.entity.Feed;
import kusitms.spin.tikitak.domain.feed.entity.FeedImage;
import kusitms.spin.tikitak.domain.feed.entity.FeedTag;
import kusitms.spin.tikitak.domain.member.entity.Member;
import kusitms.spin.tikitak.domain.place.entity.Place;
import kusitms.spin.tikitak.domain.team.entity.Team;
import kusitms.spin.tikitak.domain.team.entity.TeamMember;
import kusitms.spin.tikitak.domain.team.enums.TeamMemberRole;
import kusitms.spin.tikitak.domain.team.enums.TeamMemberStatus;
import kusitms.spin.tikitak.service.map.dto.MapPinRow;
import kusitms.spin.tikitak.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FeedRepositoryIntegrationTest extends IntegrationTest {

	@Autowired
	private FeedRepository feedRepository;

	@Test
	@DisplayName("장소별 최신 피드의 첫 번째 이미지를 썸네일로 반환하고 전체 피드 수를 포함한다")
	void findMapPinsReturnsLatestThumbnailAndFeedCountPerPlace() {
		Member member = persist(member("map1"));
		Team team = persist(team("map1"));
		TeamMember tm = persist(teamMember(member, team, TeamMemberRole.MEMBER, TeamMemberStatus.ACTIVE));
		Place placeA = persist(place("kakao-001", "카페A"));
		Place placeB = persist(place("kakao-002", "카페B"));

		// placeA: 오래된 피드
		Feed olderFeed = feed(team, tm, placeA, BASE_TIME);
		olderFeed.addImage(feedImage("https://old.jpg", 0));
		persist(olderFeed);

		// placeA: 최신 피드 (이미지 2장 — orderIndex=0이 썸네일)
		Feed newerFeed = feed(team, tm, placeA, BASE_TIME.plusHours(1));
		newerFeed.addImage(feedImage("https://new-thumb.jpg", 0));
		newerFeed.addImage(feedImage("https://new-second.jpg", 1));
		persist(newerFeed);

		// placeB: 피드 1개
		Feed feedB = feed(team, tm, placeB, BASE_TIME);
		feedB.addImage(feedImage("https://b.jpg", 0));
		persist(feedB);

		flushAndClear();

		List<MapPinRow> pins = feedRepository.findMapPinsByTeamId(team.getId());

		assertThat(pins).hasSize(2);

		MapPinRow pinA = pins.stream()
				.filter(p -> "kakao-001".equals(p.getExternalPlaceId()))
				.findFirst().orElseThrow();
		assertThat(pinA.getName()).isEqualTo("카페A");
		assertThat(pinA.getFeedCount()).isEqualTo(2L);
		assertThat(pinA.getThumbnailUrl()).isEqualTo("https://new-thumb.jpg");

		MapPinRow pinB = pins.stream()
				.filter(p -> "kakao-002".equals(p.getExternalPlaceId()))
				.findFirst().orElseThrow();
		assertThat(pinB.getFeedCount()).isEqualTo(1L);
		assertThat(pinB.getThumbnailUrl()).isEqualTo("https://b.jpg");
	}

	@Test
	@DisplayName("삭제된 피드는 핀 목록에 포함되지 않는다")
	void findMapPinsExcludesDeletedFeeds() {
		Member member = persist(member("map2"));
		Team team = persist(team("map2"));
		TeamMember tm = persist(teamMember(member, team, TeamMemberRole.MEMBER, TeamMemberStatus.ACTIVE));
		Place place = persist(place("kakao-003", "카페C"));

		Feed deletedFeed = feed(team, tm, place, BASE_TIME, BASE_TIME);
		deletedFeed.addImage(feedImage("https://deleted.jpg", 0));
		persist(deletedFeed);

		flushAndClear();

		assertThat(feedRepository.findMapPinsByTeamId(team.getId())).isEmpty();
	}

	@Test
	@DisplayName("장소가 없는 피드는 핀 목록에 포함되지 않는다")
	void findMapPinsExcludesFeedsWithoutPlace() {
		Member member = persist(member("map3"));
		Team team = persist(team("map3"));
		TeamMember tm = persist(teamMember(member, team, TeamMemberRole.MEMBER, TeamMemberStatus.ACTIVE));

		Feed noPlaceFeed = Feed.builder()
				.team(team).teamMember(tm)
				.content("장소 없는 피드")
				.createdAt(BASE_TIME).updatedAt(BASE_TIME)
				.build();
		noPlaceFeed.addImage(feedImage("https://noplace.jpg", 0));
		persist(noPlaceFeed);

		flushAndClear();

		assertThat(feedRepository.findMapPinsByTeamId(team.getId())).isEmpty();
	}

	@Test
	@DisplayName("다른 팀의 피드는 조회되지 않는다")
	void findMapPinsExcludesOtherTeamFeeds() {
		Member member = persist(member("map4"));
		Team myTeam = persist(team("map4-mine"));
		Team otherTeam = persist(team("map4-other"));
		TeamMember otherTm = persist(teamMember(member, otherTeam, TeamMemberRole.MEMBER, TeamMemberStatus.ACTIVE));
		Place place = persist(place("kakao-004", "카페D"));

		Feed otherFeed = feed(otherTeam, otherTm, place, BASE_TIME);
		otherFeed.addImage(feedImage("https://other.jpg", 0));
		persist(otherFeed);

		flushAndClear();

		assertThat(feedRepository.findMapPinsByTeamId(myTeam.getId())).isEmpty();
	}

	@Test
	@DisplayName("태그된 팀 멤버 필터는 요청한 모든 멤버가 태그된 피드만 반환한다")
	void findActiveFirstPageByTaggedTeamMemberIdsUsesAndCondition() {
		Team team = persist(team("tag-filter"));
		Member authorMember = persist(member("tag-author"));
		Member taggedMemberA = persist(member("tag-a"));
		Member taggedMemberB = persist(member("tag-b"));
		Member taggedMemberC = persist(member("tag-c"));
		TeamMember author = persist(teamMember(authorMember, team, TeamMemberRole.MEMBER, TeamMemberStatus.ACTIVE));
		TeamMember tagA = persist(teamMember(taggedMemberA, team, TeamMemberRole.MEMBER, TeamMemberStatus.ACTIVE));
		TeamMember tagB = persist(teamMember(taggedMemberB, team, TeamMemberRole.MEMBER, TeamMemberStatus.ACTIVE));
		TeamMember tagC = persist(teamMember(taggedMemberC, team, TeamMemberRole.MEMBER, TeamMemberStatus.ACTIVE));
		Place place = persist(place("kakao-tag-filter", "태그필터"));

		Feed bothTagged = feed(team, author, place, BASE_TIME.plusMinutes(3));
		bothTagged.addTag(FeedTag.builder().teamMember(tagA).build());
		bothTagged.addTag(FeedTag.builder().teamMember(tagB).build());
		persist(bothTagged);

		Feed onlyATagged = feed(team, author, place, BASE_TIME.plusMinutes(2));
		onlyATagged.addTag(FeedTag.builder().teamMember(tagA).build());
		persist(onlyATagged);

		Feed otherTagged = feed(team, author, place, BASE_TIME.plusMinutes(1));
		otherTagged.addTag(FeedTag.builder().teamMember(tagA).build());
		otherTagged.addTag(FeedTag.builder().teamMember(tagC).build());
		persist(otherTagged);

		flushAndClear();

		List<Feed> feeds = feedRepository.findActiveFirstPageByTaggedTeamMemberIds(
				team.getId(),
				null,
				null,
				List.of(tagA.getId(), tagB.getId()),
				2L,
				PageRequest.of(0, 10)
		);

		assertThat(feeds).extracting(Feed::getId).containsExactly(bothTagged.getId());
	}

	// --- helpers ---

	private Place place(String externalPlaceId, String name) {
		return Place.builder()
				.externalPlaceId(externalPlaceId)
				.name(name)
				.latitude(new BigDecimal("37.500000"))
				.longitude(new BigDecimal("127.000000"))
				.address("서울시 어딘가")
				.build();
	}

	private Feed feed(Team team, TeamMember teamMember, Place place, LocalDateTime createdAt) {
		return Feed.builder()
				.team(team).teamMember(teamMember).place(place)
				.content("테스트 피드")
				.createdAt(createdAt).updatedAt(createdAt)
				.build();
	}

	private Feed feed(Team team, TeamMember teamMember, Place place, LocalDateTime createdAt, LocalDateTime deletedAt) {
		return Feed.builder()
				.team(team).teamMember(teamMember).place(place)
				.content("삭제된 피드")
				.createdAt(createdAt).updatedAt(createdAt).deletedAt(deletedAt)
				.build();
	}

	private FeedImage feedImage(String imgUrl, int orderIndex) {
		return FeedImage.builder()
				.imgUrl(imgUrl)
				.orderIndex(orderIndex)
				.build();
	}
}
