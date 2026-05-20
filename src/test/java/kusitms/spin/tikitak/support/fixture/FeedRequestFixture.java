package kusitms.spin.tikitak.support.fixture;

import com.navercorp.fixturemonkey.FixtureMonkey;
import com.navercorp.fixturemonkey.api.introspector.FieldReflectionArbitraryIntrospector;
import kusitms.spin.tikitak.service.feed.dto.FeedRequestDTO;

import java.util.List;
import java.util.UUID;

public final class FeedRequestFixture {

	private static final FixtureMonkey FIXTURE_MONKEY = FixtureMonkey.builder()
			.objectIntrospector(FieldReflectionArbitraryIntrospector.INSTANCE)
			.build();

	private FeedRequestFixture() {
	}

	public static FeedRequestDTO.FeedCreateRequestDTO validCreateRequest(UUID mediaPublicId) {
		return FIXTURE_MONKEY.giveMeBuilder(FeedRequestDTO.FeedCreateRequestDTO.class)
				.set("content", "오늘 카페 좋았다")
				.set("mediaPublicIds", List.of(mediaPublicId))
				.set("place", null)
				.set("taggedTeamMemberIds", List.of())
				.sample();
	}

	public static FeedRequestDTO.FeedCreateRequestDTO createRequestWithMediaCount(int mediaCount) {
		return FIXTURE_MONKEY.giveMeBuilder(FeedRequestDTO.FeedCreateRequestDTO.class)
				.set("content", "오늘 카페 좋았다")
				.set("mediaPublicIds", FIXTURE_MONKEY.giveMe(UUID.class, mediaCount))
				.set("place", null)
				.set("taggedTeamMemberIds", List.of())
				.sample();
	}

	public static FeedRequestDTO.FeedCreateRequestDTO createRequestWithContent(UUID mediaPublicId, String content) {
		return FIXTURE_MONKEY.giveMeBuilder(FeedRequestDTO.FeedCreateRequestDTO.class)
				.set("content", content)
				.set("mediaPublicIds", List.of(mediaPublicId))
				.set("place", null)
				.set("taggedTeamMemberIds", List.of())
				.sample();
	}
}
