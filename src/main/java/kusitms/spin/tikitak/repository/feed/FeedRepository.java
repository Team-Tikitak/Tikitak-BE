package kusitms.spin.tikitak.repository.feed;

import jakarta.persistence.LockModeType;
import kusitms.spin.tikitak.domain.feed.entity.Feed;
import kusitms.spin.tikitak.service.home.dto.RegionRow;
import kusitms.spin.tikitak.service.map.dto.MapPinRow;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FeedRepository extends JpaRepository<Feed, Long> {

	@EntityGraph(attributePaths = { "teamMember", "teamMember.member", "place", "question", "tags", "tags.teamMember" })
	@Query("""
			select f
			from Feed f
			where f.id = :feedId
				and f.team.id = :teamId
				and f.deletedAt is null
			""")
	Optional<Feed> findActiveDetail(@Param("teamId") Long teamId, @Param("feedId") Long feedId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@EntityGraph(attributePaths = { "teamMember", "team", "place", "question" })
	@Query("""
			select f
			from Feed f
			where f.id = :feedId
				and f.team.id = :teamId
				and f.deletedAt is null
			""")
	Optional<Feed> findActiveForUpdate(@Param("teamId") Long teamId, @Param("feedId") Long feedId);

	@EntityGraph(attributePaths = { "teamMember", "teamMember.member", "place", "question" })
	@Query("""
			select distinct f
			from Feed f
			where f.team.id = :teamId
				and f.deletedAt is null
				and (
					:feedType is null
					or (:feedType = 'GENERAL' and f.question is null)
					or (:feedType = 'DAILY_QUESTION' and f.question is not null)
				)
			order by f.createdAt desc, f.id desc
			""")
	List<Feed> findActiveFirstPage(
			@Param("teamId") Long teamId,
			@Param("feedType") String feedType,
			Pageable pageable
	);

	@EntityGraph(attributePaths = { "teamMember", "teamMember.member", "place", "question" })
	@Query("""
			select distinct f
			from Feed f
			join f.place p
			where f.team.id = :teamId
				and f.deletedAt is null
				and p.region = :region
				and (
					:feedType is null
					or (:feedType = 'GENERAL' and f.question is null)
					or (:feedType = 'DAILY_QUESTION' and f.question is not null)
				)
			order by f.createdAt desc, f.id desc
			""")
	List<Feed> findActiveFirstPageByRegion(
			@Param("teamId") Long teamId,
			@Param("region") String region,
			@Param("feedType") String feedType,
			Pageable pageable
	);

	@EntityGraph(attributePaths = { "teamMember", "teamMember.member", "place", "question" })
	@Query("""
			select distinct f
			from Feed f
			join f.place p
			where f.team.id = :teamId
				and f.deletedAt is null
				and p.region = :region
				and (
					:feedType is null
					or (:feedType = 'GENERAL' and f.question is null)
					or (:feedType = 'DAILY_QUESTION' and f.question is not null)
				)
				and (
					f.createdAt < :cursorCreatedAt
					or (f.createdAt = :cursorCreatedAt and f.id < :cursorFeedId)
				)
			order by f.createdAt desc, f.id desc
			""")
	List<Feed> findActiveCursorPageByRegion(
			@Param("teamId") Long teamId,
			@Param("region") String region,
			@Param("feedType") String feedType,
			@Param("cursorCreatedAt") LocalDateTime cursorCreatedAt,
			@Param("cursorFeedId") Long cursorFeedId,
			Pageable pageable
	);

	@EntityGraph(attributePaths = { "teamMember", "teamMember.member", "place", "question" })
	@Query("""
			select distinct f
			from Feed f
			left join f.place p
			where f.team.id = :teamId
				and f.deletedAt is null
				and p.externalPlaceId = :placeId
				and (
					:feedType is null
					or (:feedType = 'GENERAL' and f.question is null)
					or (:feedType = 'DAILY_QUESTION' and f.question is not null)
				)
			order by f.createdAt desc, f.id desc
			""")
	List<Feed> findActiveFirstPageByPlaceId(
			@Param("teamId") Long teamId,
			@Param("placeId") String placeId,
			@Param("feedType") String feedType,
			Pageable pageable
	);

	@EntityGraph(attributePaths = { "teamMember", "teamMember.member", "place", "question" })
	@Query("""
			select distinct f
			from Feed f
			where f.team.id = :teamId
				and f.deletedAt is null
				and (
					:feedType is null
					or (:feedType = 'GENERAL' and f.question is null)
					or (:feedType = 'DAILY_QUESTION' and f.question is not null)
				)
				and (
					f.createdAt < :cursorCreatedAt
					or (f.createdAt = :cursorCreatedAt and f.id < :cursorFeedId)
				)
			order by f.createdAt desc, f.id desc
			""")
	List<Feed> findActiveCursorPage(
			@Param("teamId") Long teamId,
			@Param("feedType") String feedType,
			@Param("cursorCreatedAt") LocalDateTime cursorCreatedAt,
			@Param("cursorFeedId") Long cursorFeedId,
			Pageable pageable
	);

	@EntityGraph(attributePaths = { "teamMember", "teamMember.member", "place", "question" })
	@Query("""
			select distinct f
			from Feed f
			left join f.place p
			where f.team.id = :teamId
				and f.deletedAt is null
				and p.externalPlaceId = :placeId
				and (
					:feedType is null
					or (:feedType = 'GENERAL' and f.question is null)
					or (:feedType = 'DAILY_QUESTION' and f.question is not null)
				)
				and (
					f.createdAt < :cursorCreatedAt
					or (f.createdAt = :cursorCreatedAt and f.id < :cursorFeedId)
				)
			order by f.createdAt desc, f.id desc
			""")
	List<Feed> findActiveCursorPageByPlaceId(
			@Param("teamId") Long teamId,
			@Param("placeId") String placeId,
			@Param("feedType") String feedType,
			@Param("cursorCreatedAt") LocalDateTime cursorCreatedAt,
			@Param("cursorFeedId") Long cursorFeedId,
			Pageable pageable
	);

	@EntityGraph(attributePaths = { "teamMember", "teamMember.member", "place", "question" })
	@Query("""
			select f
			from Feed f
			left join f.place p
			where f.team.id = :teamId
				and f.deletedAt is null
				and (:placeId is null or p.externalPlaceId = :placeId)
				and (:region is null or p.region = :region)
				and (
					:feedType is null
					or (:feedType = 'GENERAL' and f.question is null)
					or (:feedType = 'DAILY_QUESTION' and f.question is not null)
				)
				and f.id in (
					select tag.feed.id
					from FeedTag tag
					where tag.teamMember.id in :taggedTeamMemberIds
					group by tag.feed.id
					having count(distinct tag.teamMember.id) = :taggedTeamMemberCount
				)
			order by f.createdAt desc, f.id desc
			""")
	List<Feed> findActiveFirstPageByTaggedTeamMemberIds(
			@Param("teamId") Long teamId,
			@Param("placeId") String placeId,
			@Param("region") String region,
			@Param("feedType") String feedType,
			@Param("taggedTeamMemberIds") List<Long> taggedTeamMemberIds,
			@Param("taggedTeamMemberCount") long taggedTeamMemberCount,
			Pageable pageable
	);

	@EntityGraph(attributePaths = { "teamMember", "teamMember.member", "place", "question" })
	@Query("""
			select f
			from Feed f
			left join f.place p
			where f.team.id = :teamId
				and f.deletedAt is null
				and (:placeId is null or p.externalPlaceId = :placeId)
				and (:region is null or p.region = :region)
				and (
					:feedType is null
					or (:feedType = 'GENERAL' and f.question is null)
					or (:feedType = 'DAILY_QUESTION' and f.question is not null)
				)
				and f.id in (
					select tag.feed.id
					from FeedTag tag
					where tag.teamMember.id in :taggedTeamMemberIds
					group by tag.feed.id
					having count(distinct tag.teamMember.id) = :taggedTeamMemberCount
				)
				and (
					f.createdAt < :cursorCreatedAt
					or (f.createdAt = :cursorCreatedAt and f.id < :cursorFeedId)
				)
			order by f.createdAt desc, f.id desc
			""")
	List<Feed> findActiveCursorPageByTaggedTeamMemberIds(
			@Param("teamId") Long teamId,
			@Param("placeId") String placeId,
			@Param("region") String region,
			@Param("feedType") String feedType,
			@Param("taggedTeamMemberIds") List<Long> taggedTeamMemberIds,
			@Param("taggedTeamMemberCount") long taggedTeamMemberCount,
			@Param("cursorCreatedAt") LocalDateTime cursorCreatedAt,
			@Param("cursorFeedId") Long cursorFeedId,
			Pageable pageable
	);

	@Query("""
			select f.id
			from Feed f
			where f.deletedAt is not null
				and f.deletedAt <= :cutoff
			order by f.deletedAt asc, f.id asc
			""")
	List<Long> findExpiredDeletedFeedIds(@Param("cutoff") LocalDateTime cutoff, Pageable pageable);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@EntityGraph(attributePaths = { "images", "images.media" })
	@Query("""
			select f
			from Feed f
			where f.id = :feedId
				and f.deletedAt is not null
	""")
	Optional<Feed> findDeletedForHardDelete(@Param("feedId") Long feedId);

	@EntityGraph(attributePaths = { "question", "images", "images.media" })
	@Query("""
			select f
			from Feed f
			where f.team.id = :teamId
				and f.teamMember.id = :teamMemberId
				and f.question.id = :questionId
				and f.questionAnswerDate = :answerDate
				and f.deletedAt is null
			""")
	Optional<Feed> findActiveDailyAnswer(
			@Param("teamId") Long teamId,
			@Param("teamMemberId") Long teamMemberId,
			@Param("questionId") Long questionId,
			@Param("answerDate") LocalDate answerDate
	);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@EntityGraph(attributePaths = { "teamMember", "team", "question", "images", "images.media", "tags", "tags.teamMember" })
	@Query("""
			select f
			from Feed f
			where f.team.id = :teamId
				and f.teamMember.id = :teamMemberId
				and f.question.id = :questionId
				and f.questionAnswerDate = :answerDate
				and f.deletedAt is null
			""")
	Optional<Feed> findActiveDailyAnswerForUpdate(
			@Param("teamId") Long teamId,
			@Param("teamMemberId") Long teamMemberId,
			@Param("questionId") Long questionId,
			@Param("answerDate") LocalDate answerDate
	);

	@Query(value = """
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
			""", nativeQuery = true)
	List<MapPinRow> findMapPinsByTeamId(@Param("teamId") Long teamId);

	@Query("""
			select count(f)
			from Feed f
			where f.team.id = :teamId
			  and f.deletedAt is null
			  and f.meetingDate >= :startOfMonth
			  and f.meetingDate < :startOfNextMonth
			""")
	long countActiveByTeamAndMonth(
			@Param("teamId") Long teamId,
			@Param("startOfMonth") LocalDate startOfMonth,
			@Param("startOfNextMonth") LocalDate startOfNextMonth
	);

	@Query(value = """
			SELECT f.id
			FROM feed f
			LEFT JOIN (
			    SELECT feed_id, COUNT(*) AS reaction_count
			    FROM feed_reaction
			    GROUP BY feed_id
			) r ON r.feed_id = f.id
			LEFT JOIN (
			    SELECT feed_id, COUNT(*) AS comment_count
			    FROM feed_comment
			    WHERE is_deleted = false
			    GROUP BY feed_id
			) c ON c.feed_id = f.id
			WHERE f.team_id = :teamId
			  AND f.deleted_at IS NULL
			  AND f.meeting_date >= :startOfMonth
			  AND f.meeting_date < :startOfNextMonth
			ORDER BY (COALESCE(r.reaction_count, 0) + COALESCE(c.comment_count, 0)) DESC,
			         f.created_at DESC, f.id DESC
			LIMIT 10
			""", nativeQuery = true)
	List<Long> findEveryonePickFeedIds(
			@Param("teamId") Long teamId,
			@Param("startOfMonth") LocalDate startOfMonth,
			@Param("startOfNextMonth") LocalDate startOfNextMonth
	);

	@EntityGraph(attributePaths = {"teamMember", "teamMember.member", "place", "question"})
	@Query("""
			select distinct f
			from Feed f
			where f.id in :feedIds
			  and f.team.id = :teamId
			  and f.deletedAt is null
			""")
	List<Feed> findActiveByIds(
			@Param("teamId") Long teamId,
			@Param("feedIds") Collection<Long> feedIds
	);

	@Query(value = """
			SELECT f.id
			FROM feed f
			WHERE f.team_id = :teamId
			  AND f.deleted_at IS NULL
			  AND (
			      SELECT COUNT(DISTINCT ft.team_member_id)
			      FROM feed_tag ft
			      JOIN team_member tm ON tm.id = ft.team_member_id
			      WHERE ft.feed_id = f.id
			        AND tm.team_id = :teamId
			        AND tm.status = 'ACTIVE'
			  ) = (
			      SELECT COUNT(*)
			      FROM team_member tm
			      WHERE tm.team_id = :teamId
			        AND tm.status = 'ACTIVE'
			  )
			ORDER BY f.created_at DESC, f.id DESC
			LIMIT 10
			""", nativeQuery = true)
	List<Long> findAllTaggedFeedIds(@Param("teamId") Long teamId);

	@Query(value = """
			SELECT ft1.team_member_id, ft2.team_member_id
			FROM feed_tag ft1
			JOIN feed_tag ft2
			    ON ft1.feed_id = ft2.feed_id AND ft1.team_member_id < ft2.team_member_id
			JOIN feed f ON f.id = ft1.feed_id
			WHERE f.team_id = :teamId AND f.deleted_at IS NULL
			GROUP BY ft1.team_member_id, ft2.team_member_id
			ORDER BY COUNT(*) DESC, ft1.team_member_id ASC, ft2.team_member_id ASC
			LIMIT 1
			""", nativeQuery = true)
	List<Object[]> findTopCombinationPair(@Param("teamId") Long teamId);

	@Query(value = """
			WITH pair_feeds AS (
			    SELECT f.id
			    FROM feed f
			    WHERE f.team_id = :teamId AND f.deleted_at IS NULL
			      AND EXISTS (SELECT 1 FROM feed_tag WHERE feed_id = f.id AND team_member_id = :mA)
			      AND EXISTS (SELECT 1 FROM feed_tag WHERE feed_id = f.id AND team_member_id = :mB)
			)
			SELECT ft.team_member_id
			FROM feed_tag ft
			WHERE ft.feed_id IN (SELECT id FROM pair_feeds)
			GROUP BY ft.team_member_id
			HAVING COUNT(DISTINCT ft.feed_id) = (SELECT COUNT(*) FROM pair_feeds)
			""", nativeQuery = true)
	List<Long> findAlwaysCoTaggedMemberIds(
			@Param("teamId") Long teamId,
			@Param("mA") Long mA,
			@Param("mB") Long mB
	);

	@Query(value = """
			SELECT f.id
			FROM feed f
			WHERE f.team_id = :teamId AND f.deleted_at IS NULL
			  AND EXISTS (SELECT 1 FROM feed_tag WHERE feed_id = f.id AND team_member_id = :mA)
			  AND EXISTS (SELECT 1 FROM feed_tag WHERE feed_id = f.id AND team_member_id = :mB)
			ORDER BY f.created_at DESC, f.id DESC
			LIMIT 10
			""", nativeQuery = true)
	List<Long> findCombinationFeedIds(
			@Param("teamId") Long teamId,
			@Param("mA") Long mA,
			@Param("mB") Long mB
	);

	@Query("""
			select count(f)
			from Feed f
			join f.place p
			where f.team.id = :teamId
			  and f.deletedAt is null
			  and p.region is not null
			""")
	long countActiveFeedsWithRegion(@Param("teamId") Long teamId);

	@Query(value = """
			WITH region_feeds AS (
			    SELECT f.id,
			           p.region,
			           ROW_NUMBER() OVER (PARTITION BY p.region ORDER BY f.created_at DESC, f.id DESC) AS rn,
			           COUNT(f.id) OVER (PARTITION BY p.region) AS feed_count
			    FROM feed f
			    JOIN place p ON p.id = f.place_id
			    WHERE f.team_id = :teamId
			      AND f.deleted_at IS NULL
			      AND p.region IS NOT NULL
			)
			SELECT rf.region AS region,
			       rf.feed_count AS feedCount,
			       fi.img_url AS thumbnailUrl
			FROM region_feeds rf
			LEFT JOIN feed_image fi ON fi.feed_id = rf.id AND fi.order_index = 0
			WHERE rf.rn = 1
			ORDER BY rf.feed_count DESC, rf.region ASC
			""", nativeQuery = true)
	List<RegionRow> findRegionSummaries(@Param("teamId") Long teamId);
}
