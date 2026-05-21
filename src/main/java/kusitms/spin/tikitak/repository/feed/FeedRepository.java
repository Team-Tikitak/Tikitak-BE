package kusitms.spin.tikitak.repository.feed;

import jakarta.persistence.LockModeType;
import kusitms.spin.tikitak.domain.feed.entity.Feed;
import kusitms.spin.tikitak.service.map.dto.MapPinRow;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface FeedRepository extends JpaRepository<Feed, Long> {

	@EntityGraph(attributePaths = { "teamMember", "teamMember.member", "place" })
	@Query("""
			select f
			from Feed f
			where f.id = :feedId
				and f.team.id = :teamId
				and f.deletedAt is null
			""")
	Optional<Feed> findActiveDetail(@Param("teamId") Long teamId, @Param("feedId") Long feedId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@EntityGraph(attributePaths = { "teamMember", "team", "place" })
	@Query("""
			select f
			from Feed f
			where f.id = :feedId
				and f.team.id = :teamId
				and f.deletedAt is null
			""")
	Optional<Feed> findActiveForUpdate(@Param("teamId") Long teamId, @Param("feedId") Long feedId);

	@EntityGraph(attributePaths = { "teamMember", "teamMember.member", "place" })
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

	@EntityGraph(attributePaths = { "teamMember", "teamMember.member", "place" })
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

	@EntityGraph(attributePaths = { "teamMember", "teamMember.member", "place" })
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

	@EntityGraph(attributePaths = { "teamMember", "teamMember.member", "place" })
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

	@EntityGraph(attributePaths = { "teamMember", "teamMember.member", "place", "tags", "tags.teamMember" })
	@Query("""
			select f
			from Feed f
			left join f.place p
			where f.team.id = :teamId
				and f.deletedAt is null
				and (:placeId is null or p.externalPlaceId = :placeId)
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
			@Param("feedType") String feedType,
			@Param("taggedTeamMemberIds") List<Long> taggedTeamMemberIds,
			@Param("taggedTeamMemberCount") long taggedTeamMemberCount,
			Pageable pageable
	);

	@EntityGraph(attributePaths = { "teamMember", "teamMember.member", "place" })
	@Query("""
			select f
			from Feed f
			left join f.place p
			where f.team.id = :teamId
				and f.deletedAt is null
				and (:placeId is null or p.externalPlaceId = :placeId)
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
}
