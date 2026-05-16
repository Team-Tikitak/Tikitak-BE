package kusitms.spin.tikitak.service.team;

import kusitms.spin.tikitak.domain.team.enums.TeamStatus;
import kusitms.spin.tikitak.repository.member.MemberRepository;
import kusitms.spin.tikitak.repository.team.TeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class TeamInactiveBatchService {

    private final TeamRepository teamRepository;
    private final MemberRepository memberRepository;

    @Scheduled(cron = "0 0 4 * * *")
    @Transactional
    public void deleteInactiveTeams() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(7);
        List<Long> teamIds = teamRepository.findInactiveTeamIdsForDeletion(TeamStatus.INACTIVE, cutoff);
        if (!teamIds.isEmpty()) {
            memberRepository.clearActiveTeamIdByTeamIds(teamIds);
        }
        teamRepository.bulkDeleteInactiveTeams(TeamStatus.INACTIVE, cutoff);
    }
}
