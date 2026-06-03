package kusitms.spin.tikitak.service.map;

import kusitms.spin.tikitak.domain.team.enums.TeamMemberStatus;
import kusitms.spin.tikitak.domain.team.enums.TeamStatus;
import kusitms.spin.tikitak.global.exception.BusinessException;
import kusitms.spin.tikitak.global.exception.ErrorCode;
import kusitms.spin.tikitak.repository.feed.FeedRepository;
import kusitms.spin.tikitak.repository.team.TeamMemberRepository;
import kusitms.spin.tikitak.repository.team.TeamRepository;
import kusitms.spin.tikitak.service.media.ImagePreset;
import kusitms.spin.tikitak.service.media.ImageUrlResolver;
import kusitms.spin.tikitak.service.map.dto.MapPinRow;
import kusitms.spin.tikitak.service.map.dto.MapResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MapService {

	private final FeedRepository feedRepository;
	private final TeamRepository teamRepository;
	private final TeamMemberRepository teamMemberRepository;
	private final ImageUrlResolver imageUrlResolver;

	public MapResponseDTO.MapPinListResponseDTO getMapPins(Long memberId, Long teamId) {
		validateActiveTeamMember(memberId, teamId);
		List<MapPinRow> rows = feedRepository.findMapPinsByTeamId(teamId);
		List<MapResponseDTO.MapPinDTO> pins = rows.stream()
				.map(row -> MapResponseDTO.MapPinDTO.builder()
						.placeId(row.getExternalPlaceId())
						.name(row.getName())
						.latitude(row.getLatitude())
						.longitude(row.getLongitude())
						.address(row.getAddress())
						.thumbnailUrl(imageUrlResolver.resolve(row.getThumbnailUrl(), ImagePreset.FEED_THUMB))
						.heroPreviewUrl(imageUrlResolver.resolve(row.getThumbnailUrl(), ImagePreset.FEED_HERO_PREVIEW))
						.feedCount(row.getFeedCount())
						.build())
				.toList();
		return MapResponseDTO.MapPinListResponseDTO.builder()
				.pins(pins)
				.build();
	}

	private void validateActiveTeamMember(Long memberId, Long teamId) {
		teamRepository.findById(teamId)
				.filter(team -> team.getStatus() == TeamStatus.ACTIVE)
				.orElseThrow(() -> new BusinessException(ErrorCode.TEAM009));
		teamMemberRepository.findActiveByMemberIdAndTeamId(
						memberId, teamId, TeamMemberStatus.ACTIVE, TeamStatus.ACTIVE)
				.orElseThrow(() -> new BusinessException(ErrorCode.TEAM008));
	}
}
