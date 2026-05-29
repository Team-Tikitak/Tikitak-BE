package kusitms.spin.tikitak.service.feed;

import kusitms.spin.tikitak.support.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FeedCleanupBatchServiceTest extends UnitTest {

	@Mock
	private FeedCleanupService feedCleanupService;

	private FeedCleanupBatchService feedCleanupBatchService;

	@BeforeEach
	void setUp() {
		feedCleanupBatchService = new FeedCleanupBatchService(feedCleanupService);
	}

	@Test
	@DisplayName("7일 지난 삭제 피드를 최대 100건 조회해 hard delete 한다")
	void hardDeleteExpiredFeeds() {
		when(feedCleanupService.findExpiredDeletedFeedIds(any(LocalDateTime.class), eq(100)))
				.thenReturn(List.of(1L, 2L, 3L));
		when(feedCleanupService.hardDeleteExpiredFeed(eq(1L), any(LocalDateTime.class))).thenReturn(true);
		when(feedCleanupService.hardDeleteExpiredFeed(eq(2L), any(LocalDateTime.class))).thenThrow(new RuntimeException("R2 failed"));
		when(feedCleanupService.hardDeleteExpiredFeed(eq(3L), any(LocalDateTime.class))).thenReturn(true);

		feedCleanupBatchService.hardDeleteExpiredFeeds();

		ArgumentCaptor<LocalDateTime> cutoffCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
		verify(feedCleanupService).findExpiredDeletedFeedIds(cutoffCaptor.capture(), eq(100));
		assertThat(cutoffCaptor.getValue()).isBefore(LocalDateTime.now().minusDays(6));
		verify(feedCleanupService).hardDeleteExpiredFeed(eq(1L), any(LocalDateTime.class));
		verify(feedCleanupService).hardDeleteExpiredFeed(eq(2L), any(LocalDateTime.class));
		verify(feedCleanupService).hardDeleteExpiredFeed(eq(3L), any(LocalDateTime.class));
	}
}
