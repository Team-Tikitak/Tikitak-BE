package kusitms.spin.tikitak.controller.feed;

import kusitms.spin.tikitak.global.exception.BusinessException;
import kusitms.spin.tikitak.global.exception.ErrorCode;
import kusitms.spin.tikitak.service.feed.FeedCommentService;
import kusitms.spin.tikitak.service.feed.dto.FeedCommentRequestDTO;
import kusitms.spin.tikitak.service.feed.dto.FeedCommentResponseDTO;
import kusitms.spin.tikitak.support.ApiTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FeedCommentControllerTest extends ApiTest {

	private static final Long TEAM_ID = 10L;
	private static final Long FEED_ID = 25L;
	private static final Long FEED_IMAGE_ID = 33L;
	private static final Long COMMENT_ID = 100L;

	@Mock
	private FeedCommentService feedCommentService;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = mockMvc(new FeedCommentController(feedCommentService));
	}

	@Test
	@DisplayName("GET /comments는 피드 댓글 목록을 반환한다")
	void listComments() throws Exception {
		when(feedCommentService.listComments(TEST_MEMBER_ID, TEAM_ID, FEED_ID, FEED_IMAGE_ID, null, 20))
				.thenReturn(FeedCommentResponseDTO.CommentListResponseDTO.builder()
						.items(List.of(commentItem()))
						.pageInfo(FeedCommentResponseDTO.PageInfoDTO.builder()
								.nextCursor("2026-03-04T20:35_100")
								.hasNext(true)
								.size(20)
								.build())
						.build());

		mockMvc.perform(get("/api/v1/teams/{teamId}/feeds/{feedId}/comments", TEAM_ID, FEED_ID)
						.param("feedImageId", FEED_IMAGE_ID.toString())
						.param("size", "20"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.items[0].commentId").value(COMMENT_ID))
				.andExpect(jsonPath("$.data.items[0].feedImageId").value(FEED_IMAGE_ID))
				.andExpect(jsonPath("$.data.items[0].positionX").value(0.42))
				.andExpect(jsonPath("$.data.items[0].isMine").value(true))
				.andExpect(jsonPath("$.data.pageInfo.hasNext").value(true));

		verify(feedCommentService).listComments(TEST_MEMBER_ID, TEAM_ID, FEED_ID, FEED_IMAGE_ID, null, 20);
	}

	@Test
	@DisplayName("POST /comments는 이미지 앵커 댓글을 작성하고 201을 반환한다")
	void createComment() throws Exception {
		when(feedCommentService.createComment(
				eq(TEST_MEMBER_ID),
				eq(TEAM_ID),
				eq(FEED_ID),
				any(FeedCommentRequestDTO.CommentCreateRequestDTO.class)
		)).thenReturn(commentItem());

		mockMvc.perform(post("/api/v1/teams/{teamId}/feeds/{feedId}/comments", TEAM_ID, FEED_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "feedImageId": 33,
								  "content": "좋다!",
								  "positionX": 0.42,
								  "positionY": 0.68
								}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value(201))
				.andExpect(jsonPath("$.data.commentId").value(COMMENT_ID))
				.andExpect(jsonPath("$.data.isMine").value(true));

		verify(feedCommentService).createComment(
				eq(TEST_MEMBER_ID),
				eq(TEAM_ID),
				eq(FEED_ID),
				any(FeedCommentRequestDTO.CommentCreateRequestDTO.class)
		);
	}

	@Test
	@DisplayName("PATCH /comments/{commentId}는 댓글을 수정한다")
	void updateComment() throws Exception {
		when(feedCommentService.updateComment(
				eq(TEST_MEMBER_ID),
				eq(TEAM_ID),
				eq(FEED_ID),
				eq(COMMENT_ID),
				any(FeedCommentRequestDTO.CommentUpdateRequestDTO.class)
		)).thenReturn(commentItem());

		mockMvc.perform(patch("/api/v1/teams/{teamId}/feeds/{feedId}/comments/{commentId}", TEAM_ID, FEED_ID, COMMENT_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "content": "수정됨",
								  "positionX": 0.5,
								  "positionY": 0.7
								}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.commentId").value(COMMENT_ID))
				.andExpect(jsonPath("$.data.updatedAt").exists());

		verify(feedCommentService).updateComment(
				eq(TEST_MEMBER_ID),
				eq(TEAM_ID),
				eq(FEED_ID),
				eq(COMMENT_ID),
				any(FeedCommentRequestDTO.CommentUpdateRequestDTO.class)
		);
	}

	@Test
	@DisplayName("DELETE /comments/{commentId}는 댓글을 삭제한다")
	void deleteComment() throws Exception {
		doNothing().when(feedCommentService).deleteComment(TEST_MEMBER_ID, TEAM_ID, FEED_ID, COMMENT_ID);

		mockMvc.perform(delete("/api/v1/teams/{teamId}/feeds/{feedId}/comments/{commentId}", TEAM_ID, FEED_ID, COMMENT_ID))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data").doesNotExist());

		verify(feedCommentService).deleteComment(TEST_MEMBER_ID, TEAM_ID, FEED_ID, COMMENT_ID);
	}

	@Test
	@DisplayName("비즈니스 예외가 발생하면 에러 응답을 반환한다")
	void returnsErrorResponse() throws Exception {
		when(feedCommentService.listComments(TEST_MEMBER_ID, TEAM_ID, FEED_ID, FEED_IMAGE_ID, null, 20))
				.thenThrow(new BusinessException(ErrorCode.COMMENT011));

		mockMvc.perform(get("/api/v1/teams/{teamId}/feeds/{feedId}/comments", TEAM_ID, FEED_ID)
						.param("feedImageId", FEED_IMAGE_ID.toString())
						.param("size", "20"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.code").value("COMMENT011"));
	}

	private FeedCommentResponseDTO.CommentItemDTO commentItem() {
		return FeedCommentResponseDTO.CommentItemDTO.builder()
				.commentId(COMMENT_ID)
				.feedId(FEED_ID)
				.feedImageId(FEED_IMAGE_ID)
				.content("좋다!")
				.positionX(new BigDecimal("0.420000"))
				.positionY(new BigDecimal("0.680000"))
				.author(FeedCommentResponseDTO.AuthorDTO.builder()
						.teamMemberId(101L)
						.nickname("지수")
						.profileImageUrl("https://example.com/profile.png")
						.isAnonymous(false)
						.build())
				.isMine(true)
				.createdAt(LocalDateTime.of(2026, 3, 4, 20, 35))
				.updatedAt(LocalDateTime.of(2026, 3, 4, 20, 40))
				.build();
	}
}
