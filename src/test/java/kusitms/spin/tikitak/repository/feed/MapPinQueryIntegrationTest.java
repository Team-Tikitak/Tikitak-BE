package kusitms.spin.tikitak.repository.feed;

import kusitms.spin.tikitak.domain.feed.entity.Feed;
import kusitms.spin.tikitak.domain.feed.entity.FeedImage;
import kusitms.spin.tikitak.domain.member.entity.Member;
import kusitms.spin.tikitak.domain.place.entity.Place;
import kusitms.spin.tikitak.domain.team.entity.Team;
import kusitms.spin.tikitak.domain.team.entity.TeamMember;
import kusitms.spin.tikitak.domain.team.enums.TeamMemberRole;
import kusitms.spin.tikitak.domain.team.enums.TeamMemberStatus;
import kusitms.spin.tikitak.repository.feed.projection.MapPinRow;
import kusitms.spin.tikitak.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * #220: 지도 핀 쿼리를 장소별 최신 1건 인덱스 탐색(LATERAL)으로 바꾼 뒤에도,
 * 개선 전 SQL(WITH ranked, 윈도 함수)과 같은 결과를 내는지 비교한다.
 */
class MapPinQueryIntegrationTest extends IntegrationTest {

	/**
	 * 개선 전 {@code FeedRepository.findMapPinsByTeamId} SQL 원문 (origin/develop 2579dba).
	 */
	private static final String BEFORE_SQL = """
			WITH ranked AS (
				SELECT
					f.place_id,
					f.id AS feed_id,
					COUNT(*) OVER (PARTITION BY f.place_id) AS feed_count,
					ROW_NUMBER() OVER (PARTITION BY f.place_id
									ORDER BY f.created_at DESC, f.id DESC) AS rn
				FROM feed f
				WHERE f.team_id = :teamId
				AND f.deleted_at IS NULL
				AND f.place_id IS NOT NULL
			)
			SELECT
				p.external_place_id AS externalPlaceId,
				p.name,
				p.latitude,
				p.longitude,
				p.address,
				r.feed_count AS feedCount,
				fi.img_url AS thumbnailUrl
			FROM ranked r
			JOIN place p ON p.id = r.place_id
			LEFT JOIN feed_image fi ON fi.feed_id = r.feed_id
			AND fi.order_index = 0
			WHERE r.rn = 1
			""";

	private static final Comparator<Pin> PIN_ORDER = Comparator
			.comparing(Pin::externalPlaceId)
			.thenComparing(Pin::thumbnailUrl, Comparator.nullsFirst(Comparator.naturalOrder()));

	@Autowired
	private FeedRepository feedRepository;

	@Test
	@DisplayName("모든 시나리오에서 개선 전 SQL과 행 수, 모든 필드가 같다")
	void sameRowsAsBeforeSql() {
		Seed seed = seed();

		for (Long teamId : List.of(seed.myTeamId(), seed.otherTeamId())) {
			List<Pin> before = beforePins(teamId);
			List<Pin> after = afterPins(teamId);

			assertThat(before).isNotEmpty();
			assertThat(after).hasSameSizeAs(before);
			assertThat(after).containsExactlyElementsOf(before);
		}
	}

	@Test
	@DisplayName("내 팀의 핀 집합, feedCount, 썸네일이 기대값과 같다")
	void expectedPinsForMyTeam() {
		Seed seed = seed();

		List<Pin> pins = afterPins(seed.myTeamId());

		// 정렬 기준: externalPlaceId, thumbnailUrl
		assertThat(pins).containsExactly(
				// 삭제 피드가 시각상 최신이어도 활성 피드만 세고 그중 최신을 고른다
				pin(seed, "map-deleted-latest", 1, "https://deleted-latest-active.jpg"),
				// 장소 여러 개: 최신 피드(BASE+1h)의 order_index 0 이미지. 다른 팀의 더 최신 피드는 섞이지 않는다
				pin(seed, "map-multi", 2, "https://multi-latest-0.jpg"),
				// 대표 피드에 이미지가 없으면 썸네일 null (오래된 피드의 이미지를 쓰지 않는다)
				pin(seed, "map-no-image", 2, null),
				// 대표 피드에 order_index 0이 두 행이면 핀도 두 행
				pin(seed, "map-order0-dup", 1, "https://dup-a.jpg"),
				pin(seed, "map-order0-dup", 1, "https://dup-b.jpg"),
				// 대표 피드에 order_index 1만 있으면 썸네일 null
				pin(seed, "map-order1-only", 2, null),
				// created_at 동률이면 id가 큰 피드
				pin(seed, "map-tie", 2, "https://tie-bigger-id.jpg")
		);
		// 삭제 피드만 있는 장소, 다른 팀만 쓴 장소는 핀이 없다. 장소 없는 피드는 어떤 핀에도 세지 않는다
		assertThat(pins).extracting(Pin::externalPlaceId)
				.doesNotContain("map-deleted-only", "map-other-only")
				.doesNotContainNull();
		assertThat(pins).filteredOn(pin -> "map-multi".equals(pin.externalPlaceId()))
				.singleElement()
				.satisfies(pin -> {
					assertThat(pin.name()).isEqualTo("장소-multi");
					assertThat(pin.latitude()).isEqualByComparingTo("37.5");
					assertThat(pin.longitude()).isEqualByComparingTo("127.0");
					assertThat(pin.address()).isEqualTo("서울시 multi");
				});
	}

	@Test
	@DisplayName("다른 팀은 자기 피드만으로 핀을 만든다")
	void expectedPinsForOtherTeam() {
		Seed seed = seed();

		List<Pin> pins = afterPins(seed.otherTeamId());

		assertThat(pins).containsExactly(
				pin(seed, "map-multi", 1, "https://other-team-multi.jpg"),
				pin(seed, "map-other-only", 1, "https://other-only.jpg")
		);
	}

	@Test
	@DisplayName("시각 고정 헬퍼가 DB의 created_at에 실제로 반영됐다")
	void createdAtOverrideIsApplied() {
		Seed seed = seed();

		// 시각상 최신 피드를 먼저 persist했으므로, 고정이 빠지면 persist 순서(나중 = 최신)로 결과가 뒤집힌다
		assertThat(createdAtInDb(seed.multiLatestId())).isEqualTo(BASE_TIME.plusHours(1));
		assertThat(createdAtInDb(seed.multiOlderId())).isEqualTo(BASE_TIME);
		assertThat(seed.multiLatestId()).isLessThan(seed.multiOlderId());

		// 동률 시나리오: 두 피드의 created_at이 정말 같아야 id 비교가 검증된다
		assertThat(createdAtInDb(seed.tieSmallerId())).isEqualTo(BASE_TIME.plusHours(2));
		assertThat(createdAtInDb(seed.tieBiggerId())).isEqualTo(BASE_TIME.plusHours(2));
		assertThat(seed.tieSmallerId()).isLessThan(seed.tieBiggerId());
	}

	// --- 시드 ---

	private Seed seed() {
		Member member = persist(member("map-cmp"));
		Member otherMember = persist(member("map-cmp-other"));
		Team myTeam = persist(team("map-cmp-mine"));
		Team otherTeam = persist(team("map-cmp-other"));
		TeamMember tm = persist(teamMember(member, myTeam, TeamMemberRole.MEMBER, TeamMemberStatus.ACTIVE));
		TeamMember otherTm = persist(teamMember(otherMember, otherTeam, TeamMemberRole.MEMBER, TeamMemberStatus.ACTIVE));

		Place multi = persist(place("map-multi", "장소-multi", "37.5000000000", "127.0000000000", "서울시 multi"));
		Place tie = persist(place("map-tie", "장소-tie", "37.5100000000", "127.0100000000", "서울시 tie"));
		Place deletedLatest = persist(place("map-deleted-latest", "장소-deleted-latest", "37.5200000000", "127.0200000000", "서울시 deleted-latest"));
		Place deletedOnly = persist(place("map-deleted-only", "장소-deleted-only", "37.5300000000", "127.0300000000", "서울시 deleted-only"));
		Place noImage = persist(place("map-no-image", "장소-no-image", "37.5400000000", "127.0400000000", "서울시 no-image"));
		Place order1Only = persist(place("map-order1-only", "장소-order1-only", "37.5500000000", "127.0500000000", "서울시 order1-only"));
		Place order0Dup = persist(place("map-order0-dup", "장소-order0-dup", "37.5600000000", "127.0600000000", "서울시 order0-dup"));
		Place otherOnly = persist(place("map-other-only", "장소-other-only", "37.5700000000", "127.0700000000", "서울시 other-only"));

		// 장소 여러 개 + 최신 선택: 시각상 최신 피드를 먼저 persist한다(시각 고정이 빠지면 결과가 뒤집힘)
		Feed multiLatest = feed(myTeam, tm, multi, null,
				image("https://multi-latest-0.jpg", 0), image("https://multi-latest-1.jpg", 1));
		persistWithCreatedAt(multiLatest, BASE_TIME.plusHours(1));
		Feed multiOlder = feed(myTeam, tm, multi, null, image("https://multi-older.jpg", 0));
		persistWithCreatedAt(multiOlder, BASE_TIME);

		// created_at 동률: id가 큰 피드가 최신
		Feed tieSmaller = feed(myTeam, tm, tie, null, image("https://tie-smaller-id.jpg", 0));
		persistWithCreatedAt(tieSmaller, BASE_TIME.plusHours(2));
		Feed tieBigger = feed(myTeam, tm, tie, null, image("https://tie-bigger-id.jpg", 0));
		persistWithCreatedAt(tieBigger, BASE_TIME.plusHours(2));

		// 삭제된 피드가 시각상 최신인 장소
		persistWithCreatedAt(feed(myTeam, tm, deletedLatest, BASE_TIME.plusHours(4),
				image("https://deleted-latest-deleted.jpg", 0)), BASE_TIME.plusHours(3));
		persistWithCreatedAt(feed(myTeam, tm, deletedLatest, null,
				image("https://deleted-latest-active.jpg", 0)), BASE_TIME);

		// 삭제 피드만 있는 장소
		persistWithCreatedAt(feed(myTeam, tm, deletedOnly, BASE_TIME.plusHours(1),
				image("https://deleted-only.jpg", 0)), BASE_TIME);

		// 장소 없는 피드
		persistWithCreatedAt(feed(myTeam, tm, null, null, image("https://no-place.jpg", 0)), BASE_TIME.plusHours(5));

		// 대표 피드에 이미지가 없음 (오래된 피드에는 이미지가 있음)
		persistWithCreatedAt(feed(myTeam, tm, noImage, null), BASE_TIME.plusHours(1));
		persistWithCreatedAt(feed(myTeam, tm, noImage, null, image("https://no-image-older.jpg", 0)), BASE_TIME);

		// 대표 피드에 order_index 1만 있음 (오래된 피드에는 order_index 0이 있음)
		persistWithCreatedAt(feed(myTeam, tm, order1Only, null, image("https://order1-only-1.jpg", 1)), BASE_TIME.plusHours(1));
		persistWithCreatedAt(feed(myTeam, tm, order1Only, null, image("https://order1-only-older.jpg", 0)), BASE_TIME);

		// 대표 피드에 order_index 0이 두 행 (unique 제약 없음)
		persistWithCreatedAt(feed(myTeam, tm, order0Dup, null,
				image("https://dup-a.jpg", 0), image("https://dup-b.jpg", 0)), BASE_TIME);

		// 다른 팀 피드: 같은 장소에 내 팀보다 최신, 그리고 다른 팀만 쓴 장소
		persistWithCreatedAt(feed(otherTeam, otherTm, multi, null, image("https://other-team-multi.jpg", 0)), BASE_TIME.plusHours(6));
		persistWithCreatedAt(feed(otherTeam, otherTm, otherOnly, null, image("https://other-only.jpg", 0)), BASE_TIME);

		flushAndClear();

		return new Seed(myTeam.getId(), otherTeam.getId(),
				multiLatest.getId(), multiOlder.getId(), tieSmaller.getId(), tieBigger.getId(),
				List.of(multi, tie, deletedLatest, deletedOnly, noImage, order1Only, order0Dup, otherOnly));
	}

	private record Seed(
			Long myTeamId,
			Long otherTeamId,
			Long multiLatestId,
			Long multiOlderId,
			Long tieSmallerId,
			Long tieBiggerId,
			List<Place> places
	) {
	}

	// --- 조회·비교 ---

	private record Pin(
			String externalPlaceId,
			String name,
			BigDecimal latitude,
			BigDecimal longitude,
			String address,
			long feedCount,
			String thumbnailUrl
	) {
	}

	private List<Pin> afterPins(Long teamId) {
		List<MapPinRow> rows = feedRepository.findMapPinsByTeamId(teamId);
		return rows.stream()
				.map(row -> new Pin(
						row.getExternalPlaceId(),
						row.getName(),
						normalize(row.getLatitude()),
						normalize(row.getLongitude()),
						row.getAddress(),
						row.getFeedCount(),
						row.getThumbnailUrl()))
				.sorted(PIN_ORDER)
				.toList();
	}

	@SuppressWarnings("unchecked")
	private List<Pin> beforePins(Long teamId) {
		List<Object[]> rows = entityManager().createNativeQuery(BEFORE_SQL)
				.setParameter("teamId", teamId)
				.getResultList();
		return rows.stream()
				.map(row -> new Pin(
						(String) row[0],
						(String) row[1],
						normalize((BigDecimal) row[2]),
						normalize((BigDecimal) row[3]),
						(String) row[4],
						((Number) row[5]).longValue(),
						(String) row[6]))
				.sorted(PIN_ORDER)
				.toList();
	}

	private LocalDateTime createdAtInDb(Long feedId) {
		LocalDateTime createdAt = entityManager().find(Feed.class, feedId).getCreatedAt();
		entityManager().clear();
		return createdAt;
	}

	private Pin pin(Seed seed, String externalPlaceId, long feedCount, String thumbnailUrl) {
		Place place = seed.places().stream()
				.filter(p -> p.getExternalPlaceId().equals(externalPlaceId))
				.findFirst().orElseThrow();
		return new Pin(externalPlaceId, place.getName(),
				normalize(place.getLatitude()), normalize(place.getLongitude()),
				place.getAddress(), feedCount, thumbnailUrl);
	}

	private static BigDecimal normalize(BigDecimal value) {
		return value == null ? null : value.stripTrailingZeros();
	}

	// --- 엔티티 빌더 ---

	private Place place(String externalPlaceId, String name, String latitude, String longitude, String address) {
		return Place.builder()
				.externalPlaceId(externalPlaceId)
				.name(name)
				.latitude(new BigDecimal(latitude))
				.longitude(new BigDecimal(longitude))
				.address(address)
				.build();
	}

	private Feed feed(Team team, TeamMember teamMember, Place place, LocalDateTime deletedAt, FeedImage... images) {
		Feed feed = Feed.builder()
				.team(team).teamMember(teamMember).place(place)
				.content("지도 핀 비교 피드")
				.createdAt(BASE_TIME).updatedAt(BASE_TIME).deletedAt(deletedAt)
				.build();
		for (FeedImage image : images) {
			feed.addImage(image);
		}
		return feed;
	}

	private FeedImage image(String imgUrl, int orderIndex) {
		return FeedImage.builder()
				.imgUrl(imgUrl)
				.orderIndex(orderIndex)
				.build();
	}
}
