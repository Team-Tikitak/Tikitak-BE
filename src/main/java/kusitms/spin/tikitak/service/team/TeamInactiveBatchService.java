package kusitms.spin.tikitak.service.team;

import kusitms.spin.tikitak.domain.team.entity.Team;
import kusitms.spin.tikitak.domain.team.enums.TeamStatus;
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

    @Scheduled(cron = "0 0 4 * * *")
    @Transactional
    public void deleteInactiveTeams() {
        // 매일 새벽 4시에 INACTIVE 상태이면서 deletedAt이 7일 지난 팀을 완전히 삭제
        LocalDateTime cutoff = LocalDateTime.now().minusDays(7);
        List<Team> teams = teamRepository.findAllByStatusAndDeletedAtBefore(TeamStatus.INACTIVE, cutoff);
        teamRepository.deleteAll(teams);
    }
}
