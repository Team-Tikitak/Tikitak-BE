package kusitms.spin.tikitak.domain.feed.enums;

import kusitms.spin.tikitak.domain.feed.entity.Feed;
import kusitms.spin.tikitak.domain.question.entity.Question;
import kusitms.spin.tikitak.support.UnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class FeedTypeTest extends UnitTest {

	@Test
	@DisplayName("question이 없으면 일반 피드 타입으로 계산한다")
	void fromGeneralFeed() {
		Feed feed = Feed.builder()
				.question(null)
				.build();

		assertThat(FeedType.from(feed)).isEqualTo(FeedType.GENERAL);
		assertThat(feed.getType()).isEqualTo(FeedType.GENERAL);
	}

	@Test
	@DisplayName("question이 있으면 오늘의 질문 피드 타입으로 계산한다")
	void fromDailyQuestionFeed() {
		Question question = Question.builder()
				.content("오늘 가장 기억에 남는 일은?")
				.createdAt(LocalDateTime.now())
				.build();
		Feed feed = Feed.builder()
				.question(question)
				.build();

		assertThat(FeedType.from(feed)).isEqualTo(FeedType.DAILY_QUESTION);
		assertThat(feed.getType()).isEqualTo(FeedType.DAILY_QUESTION);
	}
}
