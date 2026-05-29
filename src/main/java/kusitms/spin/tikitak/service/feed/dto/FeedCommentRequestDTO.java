package kusitms.spin.tikitak.service.feed.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

public class FeedCommentRequestDTO {

	@Getter
	@NoArgsConstructor
	@AllArgsConstructor
	public static class CommentCreateRequestDTO {
		private Long feedImageId;
		private String content;
		private BigDecimal positionX;
		private BigDecimal positionY;
	}

	@Getter
	@NoArgsConstructor
	@AllArgsConstructor
	public static class CommentUpdateRequestDTO {
		private String content;
		private BigDecimal positionX;
		private BigDecimal positionY;
	}
}
