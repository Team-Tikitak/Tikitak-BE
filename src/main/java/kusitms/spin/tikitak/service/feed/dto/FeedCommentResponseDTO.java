package kusitms.spin.tikitak.service.feed.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
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
		@Schema(
				description = "댓글 작성자 프로필 이미지 URL. profile_avatar preset이 적용됩니다.",
				example = "https://media.tikitak.space/media/profile-image/8b2e58f0-8e24-4e34-91b0-87dc86d1892a.jpg?preset=profile_avatar"
		)
		private String profileImageUrl;
		@JsonProperty("isAnonymous")
		private boolean isAnonymous;
	}
}
