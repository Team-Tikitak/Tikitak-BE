package kusitms.spin.tikitak.repository.feed;

import kusitms.spin.tikitak.domain.feed.entity.FeedReaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FeedReactionRepository extends JpaRepository<FeedReaction, Long> {

	Optional<FeedReaction> findByFeedIdAndTeamMemberId(Long feedId, Long teamMemberId);

	void deleteByFeedIdAndTeamMemberId(Long feedId, Long teamMemberId);

	@Query("""
			select fr.reactionType, count(fr)
			from FeedReaction fr
			where fr.feed.id = :feedId
			group by fr.reactionType
			""")
	List<Object[]> countByReactionType(@Param("feedId") Long feedId);

	@Query("""
			select fr.feed.id, fr.reactionType, count(fr)
			from FeedReaction fr
			where fr.feed.id in :feedIds
			group by fr.feed.id, fr.reactionType
			""")
	List<Object[]> countByReactionTypeByFeedIds(@Param("feedIds") Collection<Long> feedIds);

	@Query("""
			select fr.feed.id, fr.reactionType
			from FeedReaction fr
			where fr.feed.id in :feedIds
				and fr.teamMember.id = :teamMemberId
			""")
	List<Object[]> findMyReactions(
			@Param("feedIds") Collection<Long> feedIds,
			@Param("teamMemberId") Long teamMemberId
	);
}
