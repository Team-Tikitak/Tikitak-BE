package kusitms.spin.tikitak.service.dailyquestion;

import kusitms.spin.tikitak.domain.feed.entity.Feed;
import kusitms.spin.tikitak.domain.feed.entity.FeedImage;
import kusitms.spin.tikitak.domain.feed.entity.FeedTag;
import kusitms.spin.tikitak.domain.media.entity.Media;
import kusitms.spin.tikitak.domain.media.enums.MediaPurpose;
import kusitms.spin.tikitak.domain.media.enums.MediaStatus;
import kusitms.spin.tikitak.domain.question.entity.Question;
import kusitms.spin.tikitak.domain.team.entity.Team;
import kusitms.spin.tikitak.domain.team.entity.TeamMember;
import kusitms.spin.tikitak.domain.team.enums.TeamMemberStatus;
import kusitms.spin.tikitak.domain.team.enums.TeamStatus;
import kusitms.spin.tikitak.global.dto.PatchField;
import kusitms.spin.tikitak.global.exception.BusinessException;
import kusitms.spin.tikitak.global.exception.ErrorCode;
import kusitms.spin.tikitak.global.time.KstDateProvider;
import kusitms.spin.tikitak.repository.dailyquestion.DailyQuestionFeedRepository;
import kusitms.spin.tikitak.repository.dailyquestion.DailyQuestionMediaRepository;
import kusitms.spin.tikitak.repository.dailyquestion.DailyQuestionQuestionRepository;
import kusitms.spin.tikitak.repository.dailyquestion.DailyQuestionTeamMemberRepository;
import kusitms.spin.tikitak.repository.dailyquestion.DailyQuestionTeamRepository;
import kusitms.spin.tikitak.service.dailyquestion.dto.DailyQuestionRequestDTO;
import kusitms.spin.tikitak.service.dailyquestion.dto.DailyQuestionResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DailyQuestionService {

	private static final int MAX_CONTENT_LENGTH = 1000;
	private static final String DAILY_ANSWER_ACTIVE_KEY_CONSTRAINT = "uk_feed_daily_answer_active_key";

	private final DailyQuestionTeamRepository teamRepository;
	private final DailyQuestionTeamMemberRepository teamMemberRepository;
	private final DailyQuestionQuestionRepository questionRepository;
	private final DailyQuestionFeedRepository feedRepository;
	private final DailyQuestionMediaRepository mediaRepository;
	private final KstDateProvider dateProvider;

	public DailyQuestionResponseDTO.TodayQuestionResponseDTO getTodayQuestion(Long memberId, Long teamId) {
		TeamMember viewer = getActiveTeamMember(memberId, teamId);
		LocalDate today = dateProvider.today();
		Question todayQuestion = resolveTodayQuestion(viewer.getTeam(), today);
		Feed answer = feedRepository.findActiveDailyAnswer(teamId, viewer.getId(), todayQuestion.getId(), today)
				.orElse(null);

		return DailyQuestionResponseDTO.TodayQuestionResponseDTO.builder()
				.questionId(todayQuestion.getId())
				.content(todayQuestion.getContent())
				.date(today)
				.answered(answer != null)
				.answerFeedId(answer == null ? null : answer.getId())
				.answer(answer == null ? null : toAnswer(answer))
				.build();
	}

	@Transactional
	public DailyQuestionResponseDTO.AnswerMutationResponseDTO createMyAnswer(
			Long memberId,
			Long teamId,
			Long questionId,
			DailyQuestionRequestDTO.AnswerCreateRequestDTO request
	) {
		TeamMember author = getActiveTeamMember(memberId, teamId);
		LocalDate today = dateProvider.today();
		Question todayQuestion = resolveAndValidateTodayQuestion(author.getTeam(), today, questionId);

		if (feedRepository.findActiveDailyAnswer(teamId, author.getId(), questionId, today).isPresent()) {
			throw new BusinessException(ErrorCode.DAILY_QUESTION003);
		}

		Media media = validateNewDailyQuestionImage(memberId, request.getMediaPublicId());
		media.markUsed();

		String content = normalizeContent(request.getContent());
		Feed feed = Feed.builder()
				.team(author.getTeam())
				.teamMember(author)
				.question(todayQuestion)
				.content(content)
				.questionAnswerDate(today)
				.dailyAnswerActiveKey(dailyAnswerActiveKey(teamId, author.getId(), questionId, today))
				.build();
		feed.addImage(FeedImage.builder()
				.media(media)
				.imgUrl(media.getUrl())
				.orderIndex(0)
				.build());
		feed.addTag(FeedTag.builder()
				.teamMember(author)
				.build());

		try {
			return toMutation(feedRepository.saveDailyQuestionFeed(feed));
		} catch (DataIntegrityViolationException e) {
			if (!isDailyAnswerDuplicate(e)) {
				throw e;
			}
			throw new BusinessException(ErrorCode.DAILY_QUESTION003, e);
		}
	}

	@Transactional
	public DailyQuestionResponseDTO.AnswerMutationResponseDTO updateMyAnswer(
			Long memberId,
			Long teamId,
			Long questionId,
			DailyQuestionRequestDTO.AnswerUpdateRequestDTO request
	) {
		TeamMember editor = getActiveTeamMember(memberId, teamId);
		LocalDate today = dateProvider.today();
		resolveAndValidateTodayQuestion(editor.getTeam(), today, questionId);

		PatchField<String> contentPatch = request.getContent();
		PatchField<UUID> mediaPatch = request.getMediaPublicId();
		if (!contentPatch.isDefined() && !mediaPatch.isDefined()) {
			throw new BusinessException(ErrorCode.DAILY_QUESTION005);
		}

		Feed feed = feedRepository.findActiveDailyAnswerForUpdate(teamId, editor.getId(), questionId, today)
				.orElseThrow(() -> new BusinessException(ErrorCode.DAILY_QUESTION004));

		if (contentPatch.isDefined()) {
			feed.updateDailyAnswerContent(normalizeContent(contentPatch.getValue()));
		}
		if (mediaPatch.isDefined()) {
			UUID mediaPublicId = mediaPatch.getValue();
			if (mediaPublicId == null) {
				throw new BusinessException(ErrorCode.DAILY_QUESTION006);
			}
			replaceImage(feed, memberId, mediaPublicId);
		}

		return toMutation(feed);
	}

	private TeamMember getActiveTeamMember(Long memberId, Long teamId) {
		Team team = teamRepository.findDailyQuestionTeamById(teamId)
				.orElseThrow(() -> new BusinessException(ErrorCode.TEAM009));
		if (team.getStatus() != TeamStatus.ACTIVE) {
			throw new BusinessException(ErrorCode.TEAM009);
		}
		return teamMemberRepository.findActiveByMemberIdAndTeamId(
						memberId, teamId, TeamMemberStatus.ACTIVE, TeamStatus.ACTIVE)
				.orElseThrow(() -> new BusinessException(ErrorCode.TEAM008));
	}

	private Question resolveAndValidateTodayQuestion(Team team, LocalDate today, Long questionId) {
		Question todayQuestion = resolveTodayQuestion(team, today);
		if (!todayQuestion.getId().equals(questionId)) {
			throw new BusinessException(ErrorCode.DAILY_QUESTION002);
		}
		return todayQuestion;
	}

	private Question resolveTodayQuestion(Team team, LocalDate today) {
		List<Question> questions = questionRepository.findAvailableQuestions(today);
		if (questions.isEmpty()) {
			throw new BusinessException(ErrorCode.DAILY_QUESTION001);
		}
		LocalDate teamCreatedDate = dateProvider.toKstDate(team.getCreatedAt());
		long days = ChronoUnit.DAYS.between(teamCreatedDate, today);
		if (days < 0) {
			throw new BusinessException(ErrorCode.DAILY_QUESTION001);
		}
		return questions.get((int) (days % questions.size()));
	}

	private Media validateNewDailyQuestionImage(Long memberId, UUID mediaPublicId) {
		if (mediaPublicId == null) {
			throw new BusinessException(ErrorCode.DAILY_QUESTION006);
		}
		Media media = mediaRepository.findByPublicIdForUpdate(mediaPublicId)
				.orElseThrow(() -> new BusinessException(ErrorCode.DAILY_QUESTION007));
		if (!media.getMemberId().equals(memberId)
				|| media.getPurpose() != MediaPurpose.DAILY_QUESTION_IMAGE
				|| media.getStatus() != MediaStatus.UPLOADED) {
			throw new BusinessException(ErrorCode.DAILY_QUESTION007);
		}
		return media;
	}

	private void replaceImage(Feed feed, Long memberId, UUID mediaPublicId) {
		Media media = mediaRepository.findByPublicIdForUpdate(mediaPublicId)
				.orElseThrow(() -> new BusinessException(ErrorCode.DAILY_QUESTION007));
		Set<Long> currentMediaIds = feed.getImages().stream()
				.map(FeedImage::getMedia)
				.filter(Objects::nonNull)
				.map(Media::getId)
				.collect(Collectors.toSet());

		if (!media.getMemberId().equals(memberId) || media.getPurpose() != MediaPurpose.DAILY_QUESTION_IMAGE) {
			throw new BusinessException(ErrorCode.DAILY_QUESTION007);
		}
		if (currentMediaIds.contains(media.getId())) {
			if (media.getStatus() != MediaStatus.USED) {
				throw new BusinessException(ErrorCode.DAILY_QUESTION007);
			}
			return;
		}
		if (media.getStatus() != MediaStatus.UPLOADED) {
			throw new BusinessException(ErrorCode.DAILY_QUESTION007);
		}

		feed.getImages().stream()
				.map(FeedImage::getMedia)
				.filter(Objects::nonNull)
				.forEach(Media::markDeleted);
		media.markUsed();
		feed.replaceImages(List.of(FeedImage.builder()
				.media(media)
				.imgUrl(media.getUrl())
				.orderIndex(0)
				.build()));
	}

	private String normalizeContent(String content) {
		if (content == null || content.isBlank()) {
			return null;
		}
		String trimmed = content.trim();
		if (trimmed.length() > MAX_CONTENT_LENGTH) {
			throw new BusinessException(ErrorCode.DAILY_QUESTION009);
		}
		return trimmed;
	}

	private String dailyAnswerActiveKey(Long teamId, Long teamMemberId, Long questionId, LocalDate answerDate) {
		return teamId + ":" + teamMemberId + ":" + questionId + ":" + answerDate;
	}

	private boolean isDailyAnswerDuplicate(Throwable exception) {
		Throwable current = exception;
		while (current != null) {
			String message = current.getMessage();
			if (message != null && message.contains(DAILY_ANSWER_ACTIVE_KEY_CONSTRAINT)) {
				return true;
			}
			current = current.getCause();
		}
		return false;
	}

	private DailyQuestionResponseDTO.AnswerMutationResponseDTO toMutation(Feed feed) {
		return DailyQuestionResponseDTO.AnswerMutationResponseDTO.builder()
				.feedId(feed.getId())
				.type(feed.getType())
				.question(toQuestion(feed))
				.answer(toAnswer(feed))
				.createdAt(feed.getCreatedAt())
				.updatedAt(feed.getUpdatedAt())
				.build();
	}

	private DailyQuestionResponseDTO.QuestionDTO toQuestion(Feed feed) {
		return DailyQuestionResponseDTO.QuestionDTO.builder()
				.questionId(feed.getQuestion().getId())
				.content(feed.getQuestion().getContent())
				.answerDate(feed.getQuestionAnswerDate())
				.build();
	}

	private DailyQuestionResponseDTO.AnswerDTO toAnswer(Feed feed) {
		return DailyQuestionResponseDTO.AnswerDTO.builder()
				.content(feed.getContent())
				.imageUrl(feed.getImages().stream()
						.min(Comparator.comparing(FeedImage::getOrderIndex))
						.map(FeedImage::getImgUrl)
						.orElse(null))
				.createdAt(feed.getCreatedAt())
				.updatedAt(feed.getUpdatedAt())
				.build();
	}
}
