package kusitms.spin.tikitak.repository.feed;

import kusitms.spin.tikitak.domain.feed.entity.FeedTag;
import kusitms.spin.tikitak.domain.team.enums.TeamMemberStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface FeedTagRepository extends JpaRepository<FeedTag, Long> {

	@Query("""
			select ft.teamMember, count(ft)
			from FeedTag ft
			join ft.feed f
			where f.team.id = :teamId
			  and f.deletedAt is null
			  and f.meetingDate >= :startOfMonth
			  and f.meetingDate < :startOfNextMonth
			  and ft.teamMember.status = :status
			  and ft.teamMember.deletedAt is null
			group by ft.teamMember
			order by count(ft) desc, ft.teamMember.nickname asc
			""")
	List<Object[]> findTopTaggedMembersByTeamAndMonth(
			@Param("teamId") Long teamId,
			@Param("startOfMonth") LocalDate startOfMonth,
			@Param("startOfNextMonth") LocalDate startOfNextMonth,
			@Param("status") TeamMemberStatus status
	);
}
