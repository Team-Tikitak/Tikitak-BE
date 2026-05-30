package kusitms.spin.tikitak.service.home;

import kusitms.spin.tikitak.domain.team.entity.TeamMember;
import kusitms.spin.tikitak.domain.team.enums.TeamMemberStatus;
import kusitms.spin.tikitak.domain.team.enums.TeamStatus;
import kusitms.spin.tikitak.global.config.R2Properties;
import kusitms.spin.tikitak.global.exception.BusinessException;
import kusitms.spin.tikitak.global.exception.ErrorCode;
import kusitms.spin.tikitak.repository.feed.FeedRepository;
import kusitms.spin.tikitak.repository.feed.FeedTagRepository;
import kusitms.spin.tikitak.repository.team.TeamMemberRepository;
import kusitms.spin.tikitak.service.feed.FeedService;
import kusitms.spin.tikitak.service.home.dto.HomeResponseDTO;
import kusitms.spin.tikitak.service.me.DefaultProfileImageResolver;
import kusitms.spin.tikitak.service.home.dto.RegionRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HomeService {

	private static final int BEST_ATTENDANCE_LIMIT = 3;
	private static final int REGION_MIN_FEEDS = 3;

	private final FeedTagRepository feedTagRepository;
	private final TeamMemberRepository teamMemberRepository;
	private final FeedService feedService;
	private final FeedRepository feedRepository;
	private final DefaultProfileImageResolver defaultProfileImageResolver;
	private final R2Properties r2Properties;

	public HomeResponseDTO.BestAttendanceResponse getBestAttendance(Long memberId, Long teamId) {
		teamMemberRepository.findActiveByMemberIdAndTeamId(
						memberId, teamId, TeamMemberStatus.ACTIVE, TeamStatus.ACTIVE)
				.orElseThrow(() -> new BusinessException(ErrorCode.TEAM008));

		YearMonth currentMonth = YearMonth.now();
		LocalDateTime startOfMonth = currentMonth.atDay(1).atStartOfDay();
		LocalDateTime startOfNextMonth = currentMonth.plusMonths(1).atDay(1).atStartOfDay();

		List<Object[]> rows = feedTagRepository.findTopTaggedMembersByTeamAndMonth(
				teamId, startOfMonth, startOfNextMonth, TeamMemberStatus.ACTIVE);

		List<HomeResponseDTO.BestAttendanceMemberDTO> members = new ArrayList<>();
		int limit = Math.min(rows.size(), BEST_ATTENDANCE_LIMIT);
		for (int i = 0; i < limit; i++) {
			TeamMember tm = (TeamMember) rows.get(i)[0];
			long tagCount = (Long) rows.get(i)[1];
			members.add(HomeResponseDTO.BestAttendanceMemberDTO.builder()
					.rank(i + 1)
					.teamMemberId(tm.getId())
					.nickname(tm.getNickname())
					.profileImgUrl(defaultProfileImageResolver.resolveForTeamMember(tm))
					.tagCount(tagCount)
					.build());
		}

		return HomeResponseDTO.BestAttendanceResponse.builder()
				.month(currentMonth.getMonthValue())
				.members(members)
				.build();
	}

	public HomeResponseDTO.EveryonePickResponse getEveryonePick(Long memberId, Long teamId) {
		return HomeResponseDTO.EveryonePickResponse.builder()
				.month(YearMonth.now().getMonthValue())
				.picks(feedService.getEveryonePickItems(memberId, teamId))
				.build();
	}

	public HomeResponseDTO.AllTaggedResponse getAllTaggedFeeds(Long memberId, Long teamId) {
		return HomeResponseDTO.AllTaggedResponse.builder()
				.month(YearMonth.now().getMonthValue())
				.feeds(feedService.getAllTaggedItems(memberId, teamId))
				.build();
	}

	public HomeResponseDTO.CombinationResponse getCombination(Long memberId, Long teamId) {
		FeedService.CombinationItemsResult result = feedService.getCombinationItems(memberId, teamId);
		return HomeResponseDTO.CombinationResponse.builder()
				.month(YearMonth.now().getMonthValue())
				.combination(result.combination())
				.feeds(result.feeds())
				.build();
	}

	public HomeResponseDTO.RegionResponse getRegions(Long memberId, Long teamId) {
		teamMemberRepository.findActiveByMemberIdAndTeamId(
						memberId, teamId, TeamMemberStatus.ACTIVE, TeamStatus.ACTIVE)
				.orElseThrow(() -> new BusinessException(ErrorCode.TEAM008));

		if (feedRepository.countActiveFeedsWithRegion(teamId) < REGION_MIN_FEEDS) {
			return HomeResponseDTO.RegionResponse.builder()
					.month(YearMonth.now().getMonthValue())
					.regions(List.of())
					.build();
		}

		List<HomeResponseDTO.RegionItemDTO> regions = feedRepository.findRegionSummaries(teamId)
				.stream()
				.map(row -> HomeResponseDTO.RegionItemDTO.builder()
						.region(row.getRegion())
						.feedCount(row.getFeedCount())
						.thumbnailImageUrl(row.getThumbnailUrl())
						.build())
				.toList();

		return HomeResponseDTO.RegionResponse.builder()
				.month(YearMonth.now().getMonthValue())
				.regions(regions)
				.build();
	}

	public HomeResponseDTO.RecommendedPlacesResponse getRecommendedPlaces(Long memberId, Long teamId) {
		teamMemberRepository.findActiveByMemberIdAndTeamId(
						memberId, teamId, TeamMemberStatus.ACTIVE, TeamStatus.ACTIVE)
				.orElseThrow(() -> new BusinessException(ErrorCode.TEAM008));

		List<HomeResponseDTO.RecommendedPlaceItemDTO> shuffled = new ArrayList<>(
				MayRecommendedPlaces.all(r2Properties.getPublicBaseUrl())
		);
		Collections.shuffle(shuffled);

		return HomeResponseDTO.RecommendedPlacesResponse.builder()
				.month(YearMonth.now().getMonthValue())
				.places(shuffled.subList(0, 2))
				.build();
	}

}
