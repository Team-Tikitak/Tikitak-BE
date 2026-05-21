package kusitms.spin.tikitak.controller.feed;

import kusitms.spin.tikitak.domain.feed.enums.FeedReactionType;
import kusitms.spin.tikitak.domain.feed.enums.FeedType;
import kusitms.spin.tikitak.global.exception.BusinessException;
import kusitms.spin.tikitak.global.exception.ErrorCode;
import kusitms.spin.tikitak.service.feed.FeedService;
import kusitms.spin.tikitak.service.feed.dto.FeedRequestDTO;
import kusitms.spin.tikitak.service.feed.dto.FeedResponseDTO;
import kusitms.spin.tikitak.support.ApiTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FeedControllerTest extends ApiTest {

	private static final Long TEAM_ID = 10L;
	private static final Long FEED_ID = 25L;

	@Mock
	private FeedService feedService;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = mockMvc(new FeedController(feedService));
	}

	@Test
	@DisplayName("GET /api/v1/teams/{teamId}/feeds는 피드 목록을 반환한다")
	void listFeeds() throws Exception {
		when(feedService.listFeeds(TEST_MEMBER_ID, TEAM_ID, null, 20, "kakao_12345", "DAILY_QUESTION", null))
				.thenReturn(FeedResponseDTO.FeedListResponseDTO.builder()
						.items(List.of(listItem()))
						.pageInfo(FeedResponseDTO.PageInfoDTO.builder()
								.nextCursor("2026-03-04T20:30:00_25")
								.hasNext(true)
								.size(20)
								.build())
						.build());

		mockMvc.perform(get("/api/v1/teams/{teamId}/feeds", TEAM_ID)
						.param("size", "20")
						.param("placeId", "kakao_12345")
						.param("type", "DAILY_QUESTION"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.status").value(200))
				.andExpect(jsonPath("$.data.items[0].feedId").value(FEED_ID))
				.andExpect(jsonPath("$.data.items[0].type").value("GENERAL"))
				.andExpect(jsonPath("$.data.items[0].reactionSummary.totalCount").value(1))
				.andExpect(jsonPath("$.data.pageInfo.nextCursor").value("2026-03-04T20:30:00_25"));

		verify(feedService).listFeeds(TEST_MEMBER_ID, TEAM_ID, null, 20, "kakao_12345", null, "DAILY_QUESTION", null);
	}

	@Test
	@DisplayName("region 파라미터를 전달하면 서비스에 region이 포함돼 호출된다")
	void listFeedsByRegion() throws Exception {
		String region = "서울 강남구";
		when(feedService.listFeeds(TEST_MEMBER_ID, TEAM_ID, null, null, null, region, null, null))
				.thenReturn(FeedResponseDTO.FeedListResponseDTO.builder()
						.items(List.of(listItem()))
						.pageInfo(FeedResponseDTO.PageInfoDTO.builder()
								.nextCursor(null).hasNext(false).size(20).build())
						.build());

		mockMvc.perform(get("/api/v1/teams/{teamId}/feeds", TEAM_ID)
						.param("region", region))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items[0].feedId").value(FEED_ID));

		verify(feedService).listFeeds(TEST_MEMBER_ID, TEAM_ID, null, null, null, region, null, null);
	}

	@Test
	@DisplayName("placeId와 region을 동시에 전달하면 둘 다 서비스로 전달된다")
	void listFeedsWithBothFilters() throws Exception {
		when(feedService.listFeeds(TEST_MEMBER_ID, TEAM_ID, null, null, "kakao_12345", "서울 강남구", null, null))
				.thenReturn(FeedResponseDTO.FeedListResponseDTO.builder()
						.items(List.of())
						.pageInfo(FeedResponseDTO.PageInfoDTO.builder()
								.nextCursor(null).hasNext(false).size(20).build())
						.build());

		mockMvc.perform(get("/api/v1/teams/{teamId}/feeds", TEAM_ID)
						.param("placeId", "kakao_12345")
						.param("region", "서울 강남구"))
				.andExpect(status().isOk());

		verify(feedService).listFeeds(TEST_MEMBER_ID, TEAM_ID, null, null, "kakao_12345", "서울 강남구", null, null);
	}

	@Test
	@DisplayName("GET /api/v1/teams/{teamId}/feeds?taggedTeamMemberIds=101,102는 태그 필터 목록으로 전달된다")
	void listFeedsWithCommaSeparatedTaggedTeamMemberIds() throws Exception {
		when(feedService.listFeeds(
				eq(TEST_MEMBER_ID),
				eq(TEAM_ID),
				isNull(),
				isNull(),
				isNull(),
				isNull(),
        isNull(),
				eq(List.of(101L, 102L))
		)).thenReturn(emptyListResponse());

		mockMvc.perform(get("/api/v1/teams/{teamId}/feeds", TEAM_ID)
						.param("taggedTeamMemberIds", "101,102"))
				.andExpect(status().isOk());

		verify(feedService).listFeeds(TEST_MEMBER_ID, TEAM_ID, null, null, null, null, null, List.of(101L, 102L));
	}

	@Test
	@DisplayName("GET /api/v1/teams/{teamId}/feeds?taggedTeamMemberIds=101&taggedTeamMemberIds=102는 태그 필터 목록으로 전달된다")
	void listFeedsWithRepeatedTaggedTeamMemberIds() throws Exception {
		when(feedService.listFeeds(
				eq(TEST_MEMBER_ID),
				eq(TEAM_ID),
				isNull(),
				isNull(),
				isNull(),
				isNull(),
        isNull(),
				eq(List.of(101L, 102L))
		)).thenReturn(emptyListResponse());

		mockMvc.perform(get("/api/v1/teams/{teamId}/feeds", TEAM_ID)
						.param("taggedTeamMemberIds", "101", "102"))
				.andExpect(status().isOk());

		verify(feedService).listFeeds(TEST_MEMBER_ID, TEAM_ID, null, null, null, null, null, List.of(101L, 102L));
	}

	@Test
	@DisplayName("GET /api/v1/teams/{teamId}/feeds/{feedId}는 피드 상세를 반환한다")
	void getFeed() throws Exception {
		when(feedService.getFeed(TEST_MEMBER_ID, TEAM_ID, FEED_ID)).thenReturn(detail());

		mockMvc.perform(get("/api/v1/teams/{teamId}/feeds/{feedId}", TEAM_ID, FEED_ID))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.feedId").value(FEED_ID))
				.andExpect(jsonPath("$.data.images[0].imageUrl").value("https://example.com/feed.png"))
				.andExpect(jsonPath("$.data.place.placeId").value("kakao_12345"))
				.andExpect(jsonPath("$.data.taggedMembers[0].teamMemberId").value(101L))
				.andExpect(jsonPath("$.data.myReaction").value("TAK_LEADER"));

		verify(feedService).getFeed(TEST_MEMBER_ID, TEAM_ID, FEED_ID);
	}

	@Test
	@DisplayName("POST /api/v1/teams/{teamId}/feeds는 일반 피드를 작성하고 201을 반환한다")
	void createFeed() throws Exception {
		when(feedService.createFeed(eq(TEST_MEMBER_ID), eq(TEAM_ID), any(FeedRequestDTO.FeedCreateRequestDTO.class)))
				.thenReturn(mutation());

		mockMvc.perform(post("/api/v1/teams/{teamId}/feeds", TEAM_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "content": "오늘 카페 좋았다",
								  "mediaPublicIds": ["11111111-1111-1111-1111-111111111111"],
								  "place": {
								    "placeId": "kakao_12345",
								    "name": "카페 티키",
								    "latitude": 37.12345,
								    "longitude": 127.12345,
								    "address": "서울시 강남구"
								  },
								  "taggedTeamMemberIds": [101]
								}
				"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value(201))
				.andExpect(jsonPath("$.data.feedId").value(FEED_ID))
				.andExpect(jsonPath("$.data.thumbnailImageUrl").value("https://example.com/feed.png"));

		verify(feedService).createFeed(eq(TEST_MEMBER_ID), eq(TEAM_ID), any(FeedRequestDTO.FeedCreateRequestDTO.class));
	}

	@Test
	@DisplayName("PATCH /api/v1/teams/{teamId}/feeds/{feedId}는 피드를 수정한다")
	void updateFeed() throws Exception {
		when(feedService.updateFeed(eq(TEST_MEMBER_ID), eq(TEAM_ID), eq(FEED_ID), any(FeedRequestDTO.FeedUpdateRequestDTO.class)))
				.thenReturn(mutation());

		mockMvc.perform(patch("/api/v1/teams/{teamId}/feeds/{feedId}", TEAM_ID, FEED_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "content": "수정된 내용",
								  "mediaPublicIds": ["11111111-1111-1111-1111-111111111111"],
								  "place": null,
								  "taggedTeamMemberIds": []
								}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.feedId").value(FEED_ID))
				.andExpect(jsonPath("$.data.updatedAt").exists());

		verify(feedService).updateFeed(eq(TEST_MEMBER_ID), eq(TEAM_ID), eq(FEED_ID), any(FeedRequestDTO.FeedUpdateRequestDTO.class));
	}

	@Test
	@DisplayName("DELETE /api/v1/teams/{teamId}/feeds/{feedId}는 피드를 삭제한다")
	void deleteFeed() throws Exception {
		doNothing().when(feedService).deleteFeed(TEST_MEMBER_ID, TEAM_ID, FEED_ID);

		mockMvc.perform(delete("/api/v1/teams/{teamId}/feeds/{feedId}", TEAM_ID, FEED_ID))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data").doesNotExist());

		verify(feedService).deleteFeed(TEST_MEMBER_ID, TEAM_ID, FEED_ID);
	}

	@Test
	@DisplayName("PUT /api/v1/teams/{teamId}/feeds/{feedId}/reaction은 반응을 등록하거나 변경한다")
	void upsertReaction() throws Exception {
		when(feedService.upsertReaction(eq(TEST_MEMBER_ID), eq(TEAM_ID), eq(FEED_ID), any(FeedRequestDTO.ReactionRequestDTO.class)))
				.thenReturn(reactionResponse(FeedReactionType.TAK_LEADER));

		mockMvc.perform(put("/api/v1/teams/{teamId}/feeds/{feedId}/reaction", TEAM_ID, FEED_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "reactionType": "TAK_LEADER"
								}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.feedId").value(FEED_ID))
				.andExpect(jsonPath("$.data.myReaction").value("TAK_LEADER"))
				.andExpect(jsonPath("$.data.reactionSummary.totalCount").value(1));

		verify(feedService).upsertReaction(eq(TEST_MEMBER_ID), eq(TEAM_ID), eq(FEED_ID), any(FeedRequestDTO.ReactionRequestDTO.class));
	}

	@Test
	@DisplayName("DELETE /api/v1/teams/{teamId}/feeds/{feedId}/reaction은 내 반응을 취소한다")
	void deleteReaction() throws Exception {
		when(feedService.deleteReaction(TEST_MEMBER_ID, TEAM_ID, FEED_ID)).thenReturn(reactionResponse(null));

		mockMvc.perform(delete("/api/v1/teams/{teamId}/feeds/{feedId}/reaction", TEAM_ID, FEED_ID))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.feedId").value(FEED_ID))
				.andExpect(jsonPath("$.data.myReaction").doesNotExist())
				.andExpect(jsonPath("$.data.reactionSummary.totalCount").value(1));

		verify(feedService).deleteReaction(TEST_MEMBER_ID, TEAM_ID, FEED_ID);
	}

	@Test
	@DisplayName("비즈니스 예외가 발생하면 에러 응답을 반환한다")
	void returnsErrorResponse() throws Exception {
		when(feedService.getFeed(TEST_MEMBER_ID, TEAM_ID, FEED_ID))
				.thenThrow(new BusinessException(ErrorCode.FEED003));

		mockMvc.perform(get("/api/v1/teams/{teamId}/feeds/{feedId}", TEAM_ID, FEED_ID))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.code").value("FEED003"));
	}

	private FeedResponseDTO.FeedListItemDTO listItem() {
		return FeedResponseDTO.FeedListItemDTO.builder()
				.feedId(FEED_ID)
				.type(FeedType.GENERAL)
				.content("오늘 카페 좋았다")
				.thumbnailImageUrl("https://example.com/feed.png")
				.imageCount(1)
				.author(author())
				.place(place())
				.commentCount(0)
				.reactionSummary(summary())
				.myReaction(FeedReactionType.TAK_LEADER)
				.createdAt(LocalDateTime.of(2026, 3, 4, 20, 30))
				.build();
	}

	private FeedResponseDTO.FeedDetailResponseDTO detail() {
		return FeedResponseDTO.FeedDetailResponseDTO.builder()
				.feedId(FEED_ID)
				.type(FeedType.GENERAL)
				.content("오늘 카페 좋았다")
				.author(author())
				.images(List.of(FeedResponseDTO.ImageDTO.builder()
						.feedImageId(1L)
						.imageUrl("https://example.com/feed.png")
						.orderIndex(0)
						.build()))
				.place(place())
				.taggedMembers(List.of(taggedMember()))
				.commentCount(0)
				.reactionSummary(summary())
				.myReaction(FeedReactionType.TAK_LEADER)
				.isMine(true)
				.createdAt(LocalDateTime.of(2026, 3, 4, 20, 30))
				.updatedAt(LocalDateTime.of(2026, 3, 4, 20, 30))
				.build();
	}

	private FeedResponseDTO.FeedMutationResponseDTO mutation() {
		return FeedResponseDTO.FeedMutationResponseDTO.builder()
				.feedId(FEED_ID)
				.type(FeedType.GENERAL)
				.content("오늘 카페 좋았다")
				.thumbnailImageUrl("https://example.com/feed.png")
				.imageCount(1)
				.place(place())
				.taggedMembers(List.of(taggedMember()))
				.createdAt(LocalDateTime.of(2026, 3, 4, 20, 30))
				.updatedAt(LocalDateTime.of(2026, 3, 4, 20, 30))
				.build();
	}

	private FeedResponseDTO.FeedReactionResponseDTO reactionResponse(FeedReactionType myReaction) {
		return FeedResponseDTO.FeedReactionResponseDTO.builder()
				.feedId(FEED_ID)
				.myReaction(myReaction)
				.reactionSummary(summary())
				.build();
	}

	private FeedResponseDTO.FeedListResponseDTO emptyListResponse() {
		return FeedResponseDTO.FeedListResponseDTO.builder()
				.items(List.of())
				.pageInfo(FeedResponseDTO.PageInfoDTO.builder()
						.nextCursor(null)
						.hasNext(false)
						.size(20)
						.build())
				.build();
	}

	private FeedResponseDTO.AuthorDTO author() {
		return FeedResponseDTO.AuthorDTO.builder()
				.teamMemberId(100L)
				.nickname("민경")
				.profileImageUrl("https://example.com/profile.png")
				.isAnonymous(false)
				.build();
	}

	private FeedResponseDTO.TaggedMemberDTO taggedMember() {
		return FeedResponseDTO.TaggedMemberDTO.builder()
				.teamMemberId(101L)
				.nickname("지수")
				.profileImageUrl("https://example.com/tagged.png")
				.build();
	}

	private FeedResponseDTO.PlaceDTO place() {
		return FeedResponseDTO.PlaceDTO.builder()
				.placeId("kakao_12345")
				.name("카페 티키")
				.latitude(new java.math.BigDecimal("37.12345"))
				.longitude(new java.math.BigDecimal("127.12345"))
				.address("서울시 강남구")
				.build();
	}

	private FeedResponseDTO.ReactionSummaryDTO summary() {
		return FeedResponseDTO.ReactionSummaryDTO.builder()
				.totalCount(1)
				.items(List.of(FeedResponseDTO.ReactionCountDTO.builder()
						.reactionType(FeedReactionType.TAK_LEADER)
						.count(1)
						.build()))
				.build();
	}
}
