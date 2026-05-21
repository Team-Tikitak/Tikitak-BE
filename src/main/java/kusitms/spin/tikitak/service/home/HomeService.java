package kusitms.spin.tikitak.service.home;

import kusitms.spin.tikitak.domain.team.entity.TeamMember;
import kusitms.spin.tikitak.domain.team.enums.TeamMemberStatus;
import kusitms.spin.tikitak.domain.team.enums.TeamStatus;
import kusitms.spin.tikitak.global.exception.BusinessException;
import kusitms.spin.tikitak.global.exception.ErrorCode;
import kusitms.spin.tikitak.repository.feed.FeedTagRepository;
import kusitms.spin.tikitak.repository.team.TeamMemberRepository;
import kusitms.spin.tikitak.service.feed.FeedService;
import kusitms.spin.tikitak.service.home.dto.HomeResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HomeService {

	private static final int BEST_ATTENDANCE_LIMIT = 3;

	private final FeedTagRepository feedTagRepository;
	private final TeamMemberRepository teamMemberRepository;
	private final FeedService feedService;

	public HomeResponseDTO.BestAttendanceResponse getBestAttendance(Long memberId, Long teamId) {
		teamMemberRepository.findActiveByMemberIdAndTeamId(
						memberId, teamId, TeamMemberStatus.ACTIVE, TeamStatus.ACTIVE)
				.orElseThrow(() -> new BusinessException(ErrorCode.TEAM008));

		YearMonth currentMonth = YearMonth.now();
		LocalDate startOfMonth = currentMonth.atDay(1);
		LocalDate startOfNextMonth = currentMonth.plusMonths(1).atDay(1);

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
					.profileImgUrl(tm.getProfileImgUrl())
					.tagCount(tagCount)
					.build());
		}

		return HomeResponseDTO.BestAttendanceResponse.builder()
				.members(members)
				.build();
	}

	public HomeResponseDTO.EveryonePickResponse getEveryonePick(Long memberId, Long teamId) {
		return HomeResponseDTO.EveryonePickResponse.builder()
				.picks(feedService.getEveryonePickItems(memberId, teamId))
				.build();
	}

	public HomeResponseDTO.AllTaggedResponse getAllTaggedFeeds(Long memberId, Long teamId) {
		return HomeResponseDTO.AllTaggedResponse.builder()
				.feeds(feedService.getAllTaggedItems(memberId, teamId))
				.build();
	}

	public HomeResponseDTO.CombinationResponse getCombination(Long memberId, Long teamId) {
		FeedService.CombinationItemsResult result = feedService.getCombinationItems(memberId, teamId);
		return HomeResponseDTO.CombinationResponse.builder()
				.combination(result.combination())
				.feeds(result.feeds())
				.build();
	}
}
