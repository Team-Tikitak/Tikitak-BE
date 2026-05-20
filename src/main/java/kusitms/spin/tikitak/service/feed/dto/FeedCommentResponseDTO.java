package kusitms.spin.tikitak.service.feed.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class FeedCommentResponseDTO {

	@Getter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class CommentListResponseDTO {
		private List<CommentItemDTO> items;
		private PageInfoDTO pageInfo;
	}

	@Getter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class CommentItemDTO {
		private Long commentId;
		private Long feedId;
		private Long feedImageId;
		private String content;
		private BigDecimal positionX;
		private BigDecimal positionY;
		private AuthorDTO author;
		@JsonProperty("isMine")
		private boolean isMine;
		private LocalDateTime createdAt;
		private LocalDateTime updatedAt;
	}

	@Getter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class PageInfoDTO {
		private String nextCursor;
		private boolean hasNext;
		private int size;
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
}
