package kusitms.spin.tikitak.repository.feed;

import jakarta.persistence.LockModeType;
import kusitms.spin.tikitak.domain.feed.entity.Feed;
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
			order by f.createdAt desc, f.id desc
			""")
	List<Feed> findActiveFirstPage(
			@Param("teamId") Long teamId,
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
			order by f.createdAt desc, f.id desc
			""")
	List<Feed> findActiveFirstPageByPlaceId(
			@Param("teamId") Long teamId,
			@Param("placeId") String placeId,
			Pageable pageable
	);

	@EntityGraph(attributePaths = { "teamMember", "teamMember.member", "place" })
	@Query("""
			select distinct f
			from Feed f
			where f.team.id = :teamId
				and f.deletedAt is null
				and (
					f.createdAt < :cursorCreatedAt
					or (f.createdAt = :cursorCreatedAt and f.id < :cursorFeedId)
				)
			order by f.createdAt desc, f.id desc
			""")
	List<Feed> findActiveCursorPage(
			@Param("teamId") Long teamId,
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
					f.createdAt < :cursorCreatedAt
					or (f.createdAt = :cursorCreatedAt and f.id < :cursorFeedId)
				)
			order by f.createdAt desc, f.id desc
			""")
	List<Feed> findActiveCursorPageByPlaceId(
			@Param("teamId") Long teamId,
			@Param("placeId") String placeId,
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
}
