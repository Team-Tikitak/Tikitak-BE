package kusitms.spin.tikitak.service.dailyquestion;

import kusitms.spin.tikitak.domain.feed.entity.Feed;
import kusitms.spin.tikitak.domain.feed.entity.FeedImage;
import kusitms.spin.tikitak.domain.media.entity.Media;
import kusitms.spin.tikitak.domain.media.enums.MediaPurpose;
import kusitms.spin.tikitak.domain.media.enums.MediaStatus;
import kusitms.spin.tikitak.domain.member.entity.Member;
import kusitms.spin.tikitak.domain.notification.event.DailyAnswerPostedEvent;
import kusitms.spin.tikitak.domain.question.entity.Question;
import kusitms.spin.tikitak.domain.team.entity.Team;
import kusitms.spin.tikitak.domain.team.entity.TeamMember;
import kusitms.spin.tikitak.global.dto.PatchField;
import kusitms.spin.tikitak.global.exception.BusinessException;
import kusitms.spin.tikitak.global.exception.ErrorCode;
import kusitms.spin.tikitak.global.time.KstDateProvider;
import kusitms.spin.tikitak.service.dailyquestion.dto.DailyQuestionRequestDTO;
import kusitms.spin.tikitak.service.dailyquestion.dto.DailyQuestionResponseDTO;
import kusitms.spin.tikitak.support.UnitTest;
import kusitms.spin.tikitak.support.fake.dailyquestion.FakeDailyQuestionFeedRepository;
import kusitms.spin.tikitak.support.fake.dailyquestion.FakeDailyQuestionMediaRepository;
import kusitms.spin.tikitak.support.fake.dailyquestion.FakeDailyQuestionQuestionRepository;
import kusitms.spin.tikitak.support.fake.dailyquestion.FakeDailyQuestionTeamMemberRepository;
import kusitms.spin.tikitak.support.fake.dailyquestion.FakeDailyQuestionTeamRepository;
import kusitms.spin.tikitak.support.fake.notification.FakeApplicationEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

import static kusitms.spin.tikitak.support.fixture.MediaFixture.media;
import static kusitms.spin.tikitak.support.fixture.MemberFixture.activeMember;
import static kusitms.spin.tikitak.support.fixture.TeamFixture.activeTeam;
import static kusitms.spin.tikitak.support.fixture.TeamMemberFixture.activeMember;
import static kusitms.spin.tikitak.support.fixture.ImageUrlResolverFixture.disabledImageUrlResolver;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DailyQuestionServiceTest extends UnitTest {

	private static final Long MEMBER_ID = 1L;
	private static final Long OTHER_MEMBER_ID = 2L;
	private static final Long TEAM_ID = 10L;
	private static final Long TEAM_MEMBER_ID = 100L;
	private static final Long OTHER_TEAM_MEMBER_ID = 101L;
	private static final Long QUESTION_ID = 1L;
	private static final UUID MEDIA_PUBLIC_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
	private static final UUID NEXT_MEDIA_PUBLIC_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
	private static final LocalDate TODAY = LocalDate.of(2026, 3, 4);

	private FakeDailyQuestionTeamRepository teamRepository;
	private FakeDailyQuestionTeamMemberRepository teamMemberRepository;
	private FakeDailyQuestionQuestionRepository questionRepository;
	private FakeDailyQuestionFeedRepository feedRepository;
	private FakeDailyQuestionMediaRepository mediaRepository;
	private FakeApplicationEventPublisher eventPublisher;

	private DailyQuestionService dailyQuestionService;
	private Team team;
	private TeamMember author;
	private TeamMember otherTeamMember;
	private Question question;

	@BeforeEach
	void setUp() {
		teamRepository = new FakeDailyQuestionTeamRepository();
		teamMemberRepository = new FakeDailyQuestionTeamMemberRepository();
		questionRepository = new FakeDailyQuestionQuestionRepository();
		feedRepository = new FakeDailyQuestionFeedRepository();
		mediaRepository = new FakeDailyQuestionMediaRepository();
		eventPublisher = new FakeApplicationEventPublisher();
		dailyQuestionService = new DailyQuestionService(
				teamRepository,
				teamMemberRepository,
				questionRepository,
				feedRepository,
				mediaRepository,
				new KstDateProvider(Clock.fixed(Instant.parse("2026-03-04T12:00:00Z"), ZoneId.of("Asia/Seoul"))),
				disabledImageUrlResolver(),
				eventPublisher
		);
		team = activeTeam(TEAM_ID);
		Member member = activeMember(MEMBER_ID);
		Member otherMember = activeMember(OTHER_MEMBER_ID);
		author = activeMember(TEAM_MEMBER_ID, member, team);
		otherTeamMember = activeMember(OTHER_TEAM_MEMBER_ID, otherMember, team);
		question = question(QUESTION_ID, 1, "Today question");
		seedDailyQuestionBaseData();
	}

	@Test
	@DisplayName("오늘의 질문과 답변 여부를 조회한다")
	void getTodayQuestion() {
		DailyQuestionResponseDTO.TodayQuestionResponseDTO response =
				dailyQuestionService.getTodayQuestion(MEMBER_ID, TEAM_ID);

		assertThat(response.getQuestionId()).isEqualTo(QUESTION_ID);
		assertThat(response.getDate()).isEqualTo(TODAY);
		assertThat(response.isAnswered()).isFalse();
	}

	@Test
	@DisplayName("오늘의 질문 이미지로 답변을 작성한다")
	void createMyAnswer() {
		Media media = media(1L, MEMBER_ID, MEDIA_PUBLIC_ID,
				MediaPurpose.DAILY_QUESTION_IMAGE, MediaStatus.UPLOADED);
		mediaRepository.save(media);

		DailyQuestionResponseDTO.AnswerMutationResponseDTO response =
				dailyQuestionService.createMyAnswer(
						MEMBER_ID,
						TEAM_ID,
						QUESTION_ID,
						new DailyQuestionRequestDTO.AnswerCreateRequestDTO(" answer ", MEDIA_PUBLIC_ID)
				);

		assertThat(response.getType().name()).isEqualTo("DAILY_QUESTION");
		assertThat(response.getAnswer().getContent()).isEqualTo("answer");
		assertThat(media.getStatus()).isEqualTo(MediaStatus.USED);
		assertThat(feedRepository.findActiveDailyAnswer(TEAM_ID, TEAM_MEMBER_ID, QUESTION_ID, TODAY)).isPresent();

		assertThat(eventPublisher.getPublishedEvents()).hasSize(1);
		DailyAnswerPostedEvent event = (DailyAnswerPostedEvent) eventPublisher.getPublishedEvents().get(0);
		assertThat(event.recipientMemberIds()).containsExactly(OTHER_MEMBER_ID);
		assertThat(event.actorNickname()).isEqualTo(author.getNickname());
		assertThat(event.feedId()).isEqualTo(response.getFeedId());
		assertThat(event.teamId()).isEqualTo(TEAM_ID);
	}

	@Test
	@DisplayName("활성 팀원이 본인뿐이면 알림 이벤트를 발행하지 않는다")
	void createMyAnswerDoesNotPublishEventWhenNoOtherMembers() {
		teamMemberRepository = new FakeDailyQuestionTeamMemberRepository();
		teamMemberRepository.save(author);
		dailyQuestionService = new DailyQuestionService(
				teamRepository,
				teamMemberRepository,
				questionRepository,
				feedRepository,
				mediaRepository,
				new KstDateProvider(Clock.fixed(Instant.parse("2026-03-04T12:00:00Z"), ZoneId.of("Asia/Seoul"))),
				disabledImageUrlResolver(),
				eventPublisher
		);
		Media media = media(1L, MEMBER_ID, MEDIA_PUBLIC_ID,
				MediaPurpose.DAILY_QUESTION_IMAGE, MediaStatus.UPLOADED);
		mediaRepository.save(media);

		dailyQuestionService.createMyAnswer(
				MEMBER_ID,
				TEAM_ID,
				QUESTION_ID,
				new DailyQuestionRequestDTO.AnswerCreateRequestDTO("answer", MEDIA_PUBLIC_ID)
		);

		assertThat(eventPublisher.getPublishedEvents()).isEmpty();
	}

	@Test
	@DisplayName("생성, 조회, 수정 전반에서 fake repository 상태를 공유한다")
	void createThenGetThenUpdateAnswerWithFakeRepositories() {
		Media media = media(1L, MEMBER_ID, MEDIA_PUBLIC_ID,
				MediaPurpose.DAILY_QUESTION_IMAGE, MediaStatus.UPLOADED);
		Media nextMedia = media(2L, MEMBER_ID, NEXT_MEDIA_PUBLIC_ID,
				MediaPurpose.DAILY_QUESTION_IMAGE, MediaStatus.UPLOADED);
		mediaRepository.save(media);
		mediaRepository.save(nextMedia);

		dailyQuestionService.createMyAnswer(
				MEMBER_ID,
				TEAM_ID,
				QUESTION_ID,
				new DailyQuestionRequestDTO.AnswerCreateRequestDTO(" first answer ", MEDIA_PUBLIC_ID)
		);

		DailyQuestionResponseDTO.TodayQuestionResponseDTO todayResponse =
				dailyQuestionService.getTodayQuestion(MEMBER_ID, TEAM_ID);

		assertThat(todayResponse.isAnswered()).isTrue();
		assertThat(todayResponse.getAnswer().getContent()).isEqualTo("first answer");

		dailyQuestionService.updateMyAnswer(
				MEMBER_ID,
				TEAM_ID,
				QUESTION_ID,
				new DailyQuestionRequestDTO.AnswerUpdateRequestDTO(
						PatchField.of("updated answer"),
						PatchField.of(NEXT_MEDIA_PUBLIC_ID)
				)
		);

		DailyQuestionResponseDTO.TodayQuestionResponseDTO updatedResponse =
				dailyQuestionService.getTodayQuestion(MEMBER_ID, TEAM_ID);

		assertThat(updatedResponse.getAnswer().getContent()).isEqualTo("updated answer");
		assertThat(updatedResponse.getAnswer().getImageUrl()).isEqualTo(nextMedia.getUrl());
		assertThat(media.getStatus()).isEqualTo(MediaStatus.DELETED);
		assertThat(nextMedia.getStatus()).isEqualTo(MediaStatus.USED);
	}

	@Test
	@DisplayName("오늘의 답변이 이미 존재하면 예외가 발생한다")
	void createMyAnswerThrowsWhenDuplicated() {
		Media media = media(1L, MEMBER_ID, MEDIA_PUBLIC_ID,
				MediaPurpose.DAILY_QUESTION_IMAGE, MediaStatus.UPLOADED);
		mediaRepository.save(media);
		feedRepository.saveDailyQuestionFeed(answerFeed());

		assertThatThrownBy(() -> dailyQuestionService.createMyAnswer(
				MEMBER_ID,
				TEAM_ID,
				QUESTION_ID,
				new DailyQuestionRequestDTO.AnswerCreateRequestDTO("answer", MEDIA_PUBLIC_ID)
		)).isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.DAILY_QUESTION003));

		assertThat(media.getStatus()).isEqualTo(MediaStatus.UPLOADED);
	}

	@Test
	@DisplayName("오늘의 답변 unique 제약 조건 위반을 중복 답변 예외로 변환한다")
	void createMyAnswerThrowsWhenDailyAnswerConstraintViolated() {
		Media media = media(1L, MEMBER_ID, MEDIA_PUBLIC_ID,
				MediaPurpose.DAILY_QUESTION_IMAGE, MediaStatus.UPLOADED);
		mediaRepository.save(media);
		feedRepository.throwOnSave(new DataIntegrityViolationException("constraint [uk_feed_daily_answer_active_key]"));

		assertThatThrownBy(() -> dailyQuestionService.createMyAnswer(
				MEMBER_ID,
				TEAM_ID,
				QUESTION_ID,
				new DailyQuestionRequestDTO.AnswerCreateRequestDTO("answer", MEDIA_PUBLIC_ID)
		)).isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.DAILY_QUESTION003));
	}

	@Test
	@DisplayName("관련 없는 데이터 무결성 예외는 그대로 전파한다")
	void createMyAnswerRethrowsUnrelatedDataIntegrityViolation() {
		Media media = media(1L, MEMBER_ID, MEDIA_PUBLIC_ID,
				MediaPurpose.DAILY_QUESTION_IMAGE, MediaStatus.UPLOADED);
		DataIntegrityViolationException exception =
				new DataIntegrityViolationException("constraint [fk_feed_question]");
		mediaRepository.save(media);
		feedRepository.throwOnSave(exception);

		assertThatThrownBy(() -> dailyQuestionService.createMyAnswer(
				MEMBER_ID,
				TEAM_ID,
				QUESTION_ID,
				new DailyQuestionRequestDTO.AnswerCreateRequestDTO("answer", MEDIA_PUBLIC_ID)
		)).isSameAs(exception);
	}

	@Test
	@DisplayName("media public id가 없으면 기존 이미지를 유지한다")
	void updateMyAnswerKeepsImageWhenMediaPublicIdOmitted() {
		Feed feed = answerFeed();
		feedRepository.saveDailyQuestionFeed(feed);

		DailyQuestionResponseDTO.AnswerMutationResponseDTO response =
				dailyQuestionService.updateMyAnswer(
						MEMBER_ID,
						TEAM_ID,
						QUESTION_ID,
						new DailyQuestionRequestDTO.AnswerUpdateRequestDTO(PatchField.of("updated"), PatchField.undefined())
				);

		assertThat(response.getAnswer().getContent()).isEqualTo("updated");
		assertThat(response.getAnswer().getImageUrl()).isEqualTo("https://example.com/feed.png");
	}

	@Test
	@DisplayName("수정 요청이 비어있으면 예외가 발생한다")
	void updateMyAnswerThrowsWhenEmpty() {
		assertThatThrownBy(() -> dailyQuestionService.updateMyAnswer(
				MEMBER_ID,
				TEAM_ID,
				QUESTION_ID,
				new DailyQuestionRequestDTO.AnswerUpdateRequestDTO(PatchField.undefined(), PatchField.undefined())
		)).isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.DAILY_QUESTION005));
	}

	@Test
	@DisplayName("업로드된 이미지로 오늘의 답변 이미지를 교체한다")
	void updateMyAnswerReplacesImage() {
		Feed feed = answerFeed();
		Media newMedia = media(2L, MEMBER_ID, NEXT_MEDIA_PUBLIC_ID,
				MediaPurpose.DAILY_QUESTION_IMAGE, MediaStatus.UPLOADED);
		feedRepository.saveDailyQuestionFeed(feed);
		mediaRepository.save(newMedia);

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

	private void seedDailyQuestionBaseData() {
		teamRepository.save(team);
		teamMemberRepository.save(author);
		teamMemberRepository.save(otherTeamMember);
		questionRepository.save(question);
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
				.content("old answer")
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
