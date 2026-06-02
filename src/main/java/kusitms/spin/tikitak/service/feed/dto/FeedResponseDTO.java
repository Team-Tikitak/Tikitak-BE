package kusitms.spin.tikitak.service.feed.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
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
		private String thumbnailImageUrl;
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
		private String nextCursor;
		private boolean hasNext;
		private int size;
		private long totalCount;
	}

	@Getter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class AuthorDTO {
		private Long teamMemberId;
		private String nickname;
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
		private String imageUrl;
		private Integer orderIndex;
	}

	@Getter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class TaggedMemberDTO {
		private Long teamMemberId;
		private String nickname;
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
