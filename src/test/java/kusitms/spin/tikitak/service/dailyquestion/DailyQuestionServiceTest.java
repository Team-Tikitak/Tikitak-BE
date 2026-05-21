package kusitms.spin.tikitak.service.dailyquestion;

import kusitms.spin.tikitak.domain.feed.entity.Feed;
import kusitms.spin.tikitak.domain.feed.entity.FeedImage;
import kusitms.spin.tikitak.domain.media.entity.Media;
import kusitms.spin.tikitak.domain.media.enums.MediaPurpose;
import kusitms.spin.tikitak.domain.media.enums.MediaStatus;
import kusitms.spin.tikitak.domain.member.entity.Member;
import kusitms.spin.tikitak.domain.question.entity.Question;
import kusitms.spin.tikitak.domain.team.entity.Team;
import kusitms.spin.tikitak.domain.team.entity.TeamMember;
import kusitms.spin.tikitak.domain.team.enums.TeamMemberStatus;
import kusitms.spin.tikitak.domain.team.enums.TeamStatus;
import kusitms.spin.tikitak.global.dto.PatchField;
import kusitms.spin.tikitak.global.exception.BusinessException;
import kusitms.spin.tikitak.global.exception.ErrorCode;
import kusitms.spin.tikitak.global.time.KstDateProvider;
import kusitms.spin.tikitak.repository.feed.FeedRepository;
import kusitms.spin.tikitak.repository.media.MediaRepository;
import kusitms.spin.tikitak.repository.question.QuestionRepository;
import kusitms.spin.tikitak.repository.team.TeamMemberRepository;
import kusitms.spin.tikitak.repository.team.TeamRepository;
import kusitms.spin.tikitak.service.dailyquestion.dto.DailyQuestionRequestDTO;
import kusitms.spin.tikitak.service.dailyquestion.dto.DailyQuestionResponseDTO;
import kusitms.spin.tikitak.support.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static kusitms.spin.tikitak.support.fixture.MediaFixture.media;
import static kusitms.spin.tikitak.support.fixture.MemberFixture.activeMember;
import static kusitms.spin.tikitak.support.fixture.TeamFixture.activeTeam;
import static kusitms.spin.tikitak.support.fixture.TeamMemberFixture.activeMember;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DailyQuestionServiceTest extends UnitTest {

	private static final Long MEMBER_ID = 1L;
	private static final Long TEAM_ID = 10L;
	private static final Long TEAM_MEMBER_ID = 100L;
	private static final Long QUESTION_ID = 1L;
	private static final UUID MEDIA_PUBLIC_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
	private static final UUID NEXT_MEDIA_PUBLIC_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
	private static final LocalDate TODAY = LocalDate.of(2026, 3, 4);

	@Mock
	private TeamRepository teamRepository;
	@Mock
	private TeamMemberRepository teamMemberRepository;
	@Mock
	private QuestionRepository questionRepository;
	@Mock
	private FeedRepository feedRepository;
	@Mock
	private MediaRepository mediaRepository;

	private DailyQuestionService dailyQuestionService;
	private Team team;
	private TeamMember author;
	private Question question;

	@BeforeEach
	void setUp() {
		dailyQuestionService = new DailyQuestionService(
				teamRepository,
				teamMemberRepository,
				questionRepository,
				feedRepository,
				mediaRepository,
				new KstDateProvider(Clock.fixed(Instant.parse("2026-03-04T12:00:00Z"), ZoneId.of("Asia/Seoul")))
		);
		team = activeTeam(TEAM_ID);
		Member member = activeMember(MEMBER_ID);
		author = activeMember(TEAM_MEMBER_ID, member, team);
		question = question(QUESTION_ID, 1, "오늘 가장 기억에 남는 순간은?");
	}

	@Test
	@DisplayName("오늘의 질문과 내 답변 여부를 조회한다")
	void getTodayQuestion() {
		stubActiveAuthor();
		stubTodayQuestion();

		DailyQuestionResponseDTO.TodayQuestionResponseDTO response =
				dailyQuestionService.getTodayQuestion(MEMBER_ID, TEAM_ID);

		assertThat(response.getQuestionId()).isEqualTo(QUESTION_ID);
		assertThat(response.getDate()).isEqualTo(TODAY);
		assertThat(response.isAnswered()).isFalse();
	}

	@Test
	@DisplayName("DAILY_QUESTION_IMAGE로 오늘의 질문 답변을 작성한다")
	void createMyAnswer() {
		Media media = media(1L, MEMBER_ID, MEDIA_PUBLIC_ID,
				MediaPurpose.DAILY_QUESTION_IMAGE, MediaStatus.UPLOADED);
		stubActiveAuthor();
		stubTodayQuestion();
		when(feedRepository.findActiveDailyAnswer(TEAM_ID, TEAM_MEMBER_ID, QUESTION_ID, TODAY))
				.thenReturn(Optional.empty());
		when(mediaRepository.findByPublicIdForUpdate(MEDIA_PUBLIC_ID)).thenReturn(Optional.of(media));
		when(feedRepository.save(any(Feed.class))).thenAnswer(invocation -> invocation.getArgument(0));

		DailyQuestionResponseDTO.AnswerMutationResponseDTO response =
				dailyQuestionService.createMyAnswer(
						MEMBER_ID,
						TEAM_ID,
						QUESTION_ID,
						new DailyQuestionRequestDTO.AnswerCreateRequestDTO(" 답변 ", MEDIA_PUBLIC_ID)
				);

		assertThat(response.getType().name()).isEqualTo("DAILY_QUESTION");
		assertThat(response.getAnswer().getContent()).isEqualTo("답변");
		assertThat(media.getStatus()).isEqualTo(MediaStatus.USED);
		verify(feedRepository).save(any(Feed.class));
	}

	@Test
	@DisplayName("이미 오늘 답변했다면 중복 작성할 수 없다")
	void createMyAnswerThrowsWhenDuplicated() {
		stubActiveAuthor();
		stubTodayQuestion();
		when(feedRepository.findActiveDailyAnswer(TEAM_ID, TEAM_MEMBER_ID, QUESTION_ID, TODAY))
				.thenReturn(Optional.of(answerFeed()));

		assertThatThrownBy(() -> dailyQuestionService.createMyAnswer(
				MEMBER_ID,
				TEAM_ID,
				QUESTION_ID,
				new DailyQuestionRequestDTO.AnswerCreateRequestDTO("답변", MEDIA_PUBLIC_ID)
		)).isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.DAILY_QUESTION003));

		verify(mediaRepository, never()).findByPublicIdForUpdate(any());
	}

	@Test
	@DisplayName("수정 요청에서 이미지가 생략되면 기존 이미지를 유지한다")
	void updateMyAnswerKeepsImageWhenMediaPublicIdOmitted() {
		Feed feed = answerFeed();
		stubActiveAuthor();
		stubTodayQuestion();
		when(feedRepository.findActiveDailyAnswerForUpdate(TEAM_ID, TEAM_MEMBER_ID, QUESTION_ID, TODAY))
				.thenReturn(Optional.of(feed));

		DailyQuestionResponseDTO.AnswerMutationResponseDTO response =
				dailyQuestionService.updateMyAnswer(
						MEMBER_ID,
						TEAM_ID,
						QUESTION_ID,
						new DailyQuestionRequestDTO.AnswerUpdateRequestDTO(PatchField.of("수정"), PatchField.undefined())
				);

		assertThat(response.getAnswer().getContent()).isEqualTo("수정");
		assertThat(response.getAnswer().getImageUrl()).isEqualTo("https://example.com/feed.png");
		verify(mediaRepository, never()).findByPublicIdForUpdate(any());
	}

	@Test
	@DisplayName("수정 요청이 비어 있으면 실패한다")
	void updateMyAnswerThrowsWhenEmpty() {
		stubActiveAuthor();
		stubTodayQuestion();

		assertThatThrownBy(() -> dailyQuestionService.updateMyAnswer(
				MEMBER_ID,
				TEAM_ID,
				QUESTION_ID,
				new DailyQuestionRequestDTO.AnswerUpdateRequestDTO(PatchField.undefined(), PatchField.undefined())
		)).isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.DAILY_QUESTION005));
	}

	@Test
	@DisplayName("새 이미지로 오늘의 질문 답변 이미지를 교체한다")
	void updateMyAnswerReplacesImage() {
		Feed feed = answerFeed();
		Media newMedia = media(2L, MEMBER_ID, NEXT_MEDIA_PUBLIC_ID,
				MediaPurpose.DAILY_QUESTION_IMAGE, MediaStatus.UPLOADED);
		stubActiveAuthor();
		stubTodayQuestion();
		when(feedRepository.findActiveDailyAnswerForUpdate(TEAM_ID, TEAM_MEMBER_ID, QUESTION_ID, TODAY))
				.thenReturn(Optional.of(feed));
		when(mediaRepository.findByPublicIdForUpdate(NEXT_MEDIA_PUBLIC_ID)).thenReturn(Optional.of(newMedia));

		DailyQuestionResponseDTO.AnswerMutationResponseDTO response =
				dailyQuestionService.updateMyAnswer(
						MEMBER_ID,
						TEAM_ID,
						QUESTION_ID,
						new DailyQuestionRequestDTO.AnswerUpdateRequestDTO(PatchField.undefined(), PatchField.of(NEXT_MEDIA_PUBLIC_ID))
				);

		assertThat(response.getAnswer().getImageUrl()).isEqualTo(newMedia.getUrl());
		assertThat(newMedia.getStatus()).isEqualTo(MediaStatus.USED);
	}

	private void stubActiveAuthor() {
		when(teamRepository.findById(TEAM_ID)).thenReturn(Optional.of(team));
		when(teamMemberRepository.findActiveByMemberIdAndTeamId(
				eq(MEMBER_ID),
				eq(TEAM_ID),
				eq(TeamMemberStatus.ACTIVE),
				eq(TeamStatus.ACTIVE)
		)).thenReturn(Optional.of(author));
	}

	private void stubTodayQuestion() {
		when(questionRepository.findAvailableQuestions(TODAY)).thenReturn(List.of(question));
	}

	private Feed answerFeed() {
		Media media = media(1L, MEMBER_ID, MEDIA_PUBLIC_ID,
				MediaPurpose.DAILY_QUESTION_IMAGE, MediaStatus.USED);
		Feed feed = Feed.builder()
				.id(25L)
				.team(team)
				.teamMember(author)
				.question(question)
				.questionAnswerDate(TODAY)
				.dailyAnswerActiveKey("10:100:1:2026-03-04")
				.content("기존 답변")
				.createdAt(LocalDateTime.of(2026, 3, 4, 20, 30))
				.updatedAt(LocalDateTime.of(2026, 3, 4, 20, 30))
				.build();
		feed.addImage(FeedImage.builder()
				.media(media)
				.imgUrl(media.getUrl())
				.orderIndex(0)
				.build());
		return feed;
	}

	private Question question(Long id, Integer sequence, String content) {
		return Question.builder()
				.id(id)
				.sequence(sequence)
				.content(content)
				.active(true)
				.effectiveFrom(LocalDate.of(2025, 1, 1))
				.createdAt(LocalDateTime.of(2026, 3, 4, 0, 0))
				.updatedAt(LocalDateTime.of(2026, 3, 4, 0, 0))
				.build();
	}
}
