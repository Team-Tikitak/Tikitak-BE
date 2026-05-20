package kusitms.spin.tikitak.service.feed;

import kusitms.spin.tikitak.domain.feed.entity.Feed;
import kusitms.spin.tikitak.domain.feed.entity.FeedImage;
import kusitms.spin.tikitak.domain.media.entity.Media;
import kusitms.spin.tikitak.domain.media.entity.ObjectDeleteOutbox;
import kusitms.spin.tikitak.global.config.R2Properties;
import kusitms.spin.tikitak.repository.feed.FeedRepository;
import kusitms.spin.tikitak.repository.media.MediaRepository;
import kusitms.spin.tikitak.service.media.ObjectDeleteOutboxProcessor;
import kusitms.spin.tikitak.service.media.ObjectDeleteOutboxService;
import kusitms.spin.tikitak.support.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static kusitms.spin.tikitak.support.fixture.MediaFixture.uploadedFeedImage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FeedCleanupServiceTest extends UnitTest {

	private static final Long FEED_ID = 25L;
	private static final Long MEDIA_ID = 100L;
	private static final Long MEMBER_ID = 1L;
	private static final UUID MEDIA_PUBLIC_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
	private static final String BUCKET_NAME = "test-bucket";

	@Mock
	private FeedRepository feedRepository;

	@Mock
	private MediaRepository mediaRepository;

	@Mock
	private ObjectDeleteOutboxService objectDeleteOutboxService;

	@Mock
	private ObjectDeleteOutboxProcessor objectDeleteOutboxProcessor;

	@Mock
	private R2Properties r2Properties;

	private FeedCleanupService feedCleanupService;

	@BeforeEach
	void setUp() {
		feedCleanupService = new FeedCleanupService(
				feedRepository,
				mediaRepository,
				objectDeleteOutboxService,
				objectDeleteOutboxProcessor,
				r2Properties
		);
	}

	@Test
	@DisplayName("삭제 후 7일이 지난 피드의 R2 객체와 DB row를 hard delete 한다")
	void hardDeleteExpiredFeed() {
		LocalDateTime cutoff = LocalDateTime.of(2026, 3, 11, 20, 30);
		Media media = uploadedFeedImage(MEDIA_ID, MEMBER_ID, MEDIA_PUBLIC_ID);
		Feed feed = Feed.builder()
				.id(FEED_ID)
				.deletedAt(cutoff.minusSeconds(1))
				.build();
		feed.addImage(FeedImage.builder()
				.media(media)
				.imgUrl(media.getUrl())
				.orderIndex(0)
				.build());
		when(feedRepository.findDeletedForHardDelete(FEED_ID)).thenReturn(Optional.of(feed));
		when(r2Properties.getBucketName()).thenReturn(BUCKET_NAME);
		when(objectDeleteOutboxService.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

		boolean deleted = feedCleanupService.hardDeleteExpiredFeed(FEED_ID, cutoff);

		assertThat(deleted).isTrue();
		ObjectDeleteOutbox deleteRequest = captureSavedDeleteRequest();
		assertThat(deleteRequest.getBucket()).isEqualTo(BUCKET_NAME);
		assertThat(deleteRequest.getObjectKey()).isEqualTo(media.getKey());
		assertThat(deleteRequest.getMediaId()).isEqualTo(media.getId());
		verify(objectDeleteOutboxProcessor).process(deleteRequest.getId());
		verify(feedRepository).delete(feed);
		verify(feedRepository).flush();
		verify(mediaRepository).deleteAll(List.of(media));
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private ObjectDeleteOutbox captureSavedDeleteRequest() {
		ArgumentCaptor<List> outboxCaptor = ArgumentCaptor.forClass(List.class);
		verify(objectDeleteOutboxService).saveAll(outboxCaptor.capture());
		return (ObjectDeleteOutbox) outboxCaptor.getValue().get(0);
	}
}
