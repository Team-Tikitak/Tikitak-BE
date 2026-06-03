package kusitms.spin.tikitak.service.map;

import kusitms.spin.tikitak.domain.team.entity.Team;
import kusitms.spin.tikitak.domain.team.entity.TeamMember;
import kusitms.spin.tikitak.domain.team.enums.TeamMemberStatus;
import kusitms.spin.tikitak.domain.team.enums.TeamStatus;
import kusitms.spin.tikitak.repository.feed.FeedRepository;
import kusitms.spin.tikitak.repository.team.TeamMemberRepository;
import kusitms.spin.tikitak.repository.team.TeamRepository;
import kusitms.spin.tikitak.service.map.dto.MapPinRow;
import kusitms.spin.tikitak.service.map.dto.MapResponseDTO;
import kusitms.spin.tikitak.service.media.ImagePreset;
import kusitms.spin.tikitak.service.media.ImageUrlResolver;
import kusitms.spin.tikitak.support.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class MapServiceTest extends UnitTest {

	private static final Long MEMBER_ID = 1L;
	private static final Long TEAM_ID = 10L;
	private static final String IMAGE_URL = "https://media.tikitak.space/media/feed-image/test.jpg";

	@Mock
	private FeedRepository feedRepository;

	@Mock
	private TeamRepository teamRepository;

	@Mock
	private TeamMemberRepository teamMemberRepository;

	@Mock
	private ImageUrlResolver imageUrlResolver;

	private MapService mapService;

	@BeforeEach
	void setUp() {
		mapService = new MapService(feedRepository, teamRepository, teamMemberRepository, imageUrlResolver);
	}

	@Test
	@DisplayName("지도 핀 목록 조회 시 썸네일과 hero preview URL에 각각의 preset을 적용한다")
	void getMapPinsReturnsHeroPreviewUrl() {
		when(teamRepository.findById(TEAM_ID))
				.thenReturn(Optional.of(Team.builder()
						.status(TeamStatus.ACTIVE)
						.build()));
		when(teamMemberRepository.findActiveByMemberIdAndTeamId(
				MEMBER_ID, TEAM_ID, TeamMemberStatus.ACTIVE, TeamStatus.ACTIVE))
				.thenReturn(Optional.of(TeamMember.builder()
						.status(TeamMemberStatus.ACTIVE)
						.build()));
		when(feedRepository.findMapPinsByTeamId(TEAM_ID))
				.thenReturn(List.of(mapPinRow()));
		when(imageUrlResolver.resolve(IMAGE_URL, ImagePreset.FEED_THUMB))
				.thenReturn(IMAGE_URL + "?preset=feed_thumb");
		when(imageUrlResolver.resolve(IMAGE_URL, ImagePreset.FEED_HERO_PREVIEW))
				.thenReturn(IMAGE_URL + "?preset=feed_hero_preview");

		MapResponseDTO.MapPinListResponseDTO response = mapService.getMapPins(MEMBER_ID, TEAM_ID);

		assertThat(response.getPins()).hasSize(1);
		MapResponseDTO.MapPinDTO pin = response.getPins().get(0);
		assertThat(pin.getThumbnailUrl()).isEqualTo(IMAGE_URL + "?preset=feed_thumb");
		assertThat(pin.getHeroPreviewUrl()).isEqualTo(IMAGE_URL + "?preset=feed_hero_preview");
	}

	private MapPinRow mapPinRow() {
		return new MapPinRow() {
			@Override
			public String getExternalPlaceId() {
				return "kakao-001";
			}

			@Override
			public String getName() {
				return "Place";
			}

			@Override
			public BigDecimal getLatitude() {
				return new BigDecimal("37.500000");
			}

			@Override
			public BigDecimal getLongitude() {
				return new BigDecimal("127.000000");
			}

			@Override
			public String getAddress() {
				return "Seoul";
			}

			@Override
			public Long getFeedCount() {
				return 1L;
			}

			@Override
			public String getThumbnailUrl() {
				return IMAGE_URL;
			}
		};
	}
}
