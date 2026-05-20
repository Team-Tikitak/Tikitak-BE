package kusitms.spin.tikitak.service.feed.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public class FeedRequestDTO {

	@Getter
	@NoArgsConstructor
	@AllArgsConstructor
	public static class FeedCreateRequestDTO {
		private String content;
		private List<UUID> mediaPublicIds;
		@Valid
		private PlaceRequestDTO place;
		private List<Long> taggedTeamMemberIds;
	}

	@Getter
	@NoArgsConstructor
	@AllArgsConstructor
	public static class FeedUpdateRequestDTO {
		private String content;
		private List<UUID> mediaPublicIds;
		@Valid
		private PlaceRequestDTO place;
		private List<Long> taggedTeamMemberIds;
	}

	@Getter
	@NoArgsConstructor
	@AllArgsConstructor
	public static class PlaceRequestDTO {
		private String placeId;
		private String name;
		private BigDecimal latitude;
		private BigDecimal longitude;
		private String address;
	}

	@Getter
	@NoArgsConstructor
	@AllArgsConstructor
	public static class ReactionRequestDTO {
		@NotNull
		private String reactionType;
	}
}
