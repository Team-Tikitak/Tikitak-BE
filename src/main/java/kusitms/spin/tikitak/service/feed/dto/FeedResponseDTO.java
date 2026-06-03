package kusitms.spin.tikitak.service.feed.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import kusitms.spin.tikitak.domain.feed.enums.FeedReactionType;
import kusitms.spin.tikitak.domain.feed.enums.FeedType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class FeedResponseDTO {

	@Getter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class FeedListResponseDTO {
		private List<FeedListItemDTO> items;
		private PageInfoDTO pageInfo;
	}

	@Getter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class FeedListItemDTO {
		private Long feedId;
		private FeedType type;
		private String content;
		@Schema(
				description = "피드 목록 썸네일 이미지 URL. feed_thumb preset이 적용됩니다.",
				example = "https://media.tikitak.space/media/feed-image/8b2e58f0-8e24-4e34-91b0-87dc86d1892a.jpg?preset=feed_thumb"
		)
		private String thumbnailImageUrl;
		@Schema(
				description = "피드 상세 전환 애니메이션용 저용량 이미지 URL. 이미지 최적화가 활성화된 R2 이미지인 경우 feed_hero_preview preset이 적용됩니다.",
				example = "https://media.tikitak.space/media/feed-image/8b2e58f0-8e24-4e34-91b0-87dc86d1892a.jpg?preset=feed_hero_preview"
		)
		private String heroPreviewUrl;
		private int imageCount;
		private AuthorDTO author;
		private List<TaggedMemberDTO> taggedMembers;
		private PlaceDTO place;
		private QuestionDTO question;
		private long commentCount;
		private ReactionSummaryDTO reactionSummary;
		private FeedReactionType myReaction;
		private LocalDateTime createdAt;
	}

	@Getter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class FeedDetailResponseDTO {
		private Long feedId;
		private FeedType type;
		private String content;
		private AuthorDTO author;
		private List<ImageDTO> images;
		private PlaceDTO place;
		private QuestionDTO question;
		private List<TaggedMemberDTO> taggedMembers;
		private long commentCount;
		private ReactionSummaryDTO reactionSummary;
		private FeedReactionType myReaction;
		@JsonProperty("isMine")
		private boolean isMine;
		private LocalDateTime createdAt;
		private LocalDateTime updatedAt;
	}

	@Getter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class FeedMutationResponseDTO {
		private Long feedId;
		private FeedType type;
		private String content;
		@Schema(
				description = "피드 대표 썸네일 이미지 URL. feed_thumb preset이 적용됩니다.",
				example = "https://media.tikitak.space/media/feed-image/8b2e58f0-8e24-4e34-91b0-87dc86d1892a.jpg?preset=feed_thumb"
		)
		private String thumbnailImageUrl;
		private int imageCount;
		private PlaceDTO place;
		private QuestionDTO question;
		private List<TaggedMemberDTO> taggedMembers;
		private LocalDateTime createdAt;
		private LocalDateTime updatedAt;
	}

	@Getter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class FeedReactionResponseDTO {
		private Long feedId;
		private FeedReactionType myReaction;
		private ReactionSummaryDTO reactionSummary;
	}

	@Getter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class PageInfoDTO {
		@Schema(description = "다음 페이지 조회용 커서", example = "2026-03-04T20:30:00_25", nullable = true)
		private String nextCursor;
		@Schema(description = "다음 페이지 존재 여부", example = "true")
		private boolean hasNext;
		@Schema(description = "현재 요청 페이지 크기", example = "20")
		private int size;
		@Schema(description = "현재 조회 조건이 반영된 전체 피드 수", example = "137")
		private long totalCount;
	}

	@Getter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class AuthorDTO {
		private Long teamMemberId;
		private String nickname;
		@Schema(
				description = "작성자 프로필 이미지 URL. profile_avatar preset이 적용됩니다.",
				example = "https://media.tikitak.space/media/profile-image/8b2e58f0-8e24-4e34-91b0-87dc86d1892a.jpg?preset=profile_avatar"
		)
		private String profileImageUrl;
		@JsonProperty("isAnonymous")
		private boolean isAnonymous;
	}

	@Getter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class PlaceDTO {
		private String placeId;
		private String name;
		private BigDecimal latitude;
		private BigDecimal longitude;
		private String address;
	}

	@Getter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class QuestionDTO {
		private Long questionId;
		private String content;
		private LocalDate answerDate;
	}

	@Getter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class ImageDTO {
		private Long feedImageId;
		private UUID mediaPublicId;
		@Schema(
				description = "피드 상세 이미지 URL. feed_detail preset이 적용됩니다.",
				example = "https://media.tikitak.space/media/feed-image/8b2e58f0-8e24-4e34-91b0-87dc86d1892a.jpg?preset=feed_detail"
		)
		private String imageUrl;
		@Schema(
				description = "피드 상세 이미지와 같은 비율의 저용량 preview URL. 이미지 최적화가 활성화된 R2 이미지인 경우 feed_hero_preview preset이 적용됩니다.",
				example = "https://media.tikitak.space/media/feed-image/8b2e58f0-8e24-4e34-91b0-87dc86d1892a.jpg?preset=feed_hero_preview"
		)
		private String heroPreviewUrl;
		private Integer orderIndex;
	}

	@Getter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class TaggedMemberDTO {
		private Long teamMemberId;
		private String nickname;
		@Schema(
				description = "태그된 멤버 프로필 이미지 URL. profile_avatar preset이 적용됩니다.",
				example = "https://media.tikitak.space/media/profile-image/8b2e58f0-8e24-4e34-91b0-87dc86d1892a.jpg?preset=profile_avatar"
		)
		private String profileImageUrl;
	}

	@Getter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class ReactionSummaryDTO {
		private long totalCount;
		private List<ReactionCountDTO> items;
	}

	@Getter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class ReactionCountDTO {
		private FeedReactionType reactionType;
		private long count;
	}
}
