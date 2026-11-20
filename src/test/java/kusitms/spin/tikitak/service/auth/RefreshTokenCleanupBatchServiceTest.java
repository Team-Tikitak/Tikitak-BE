package kusitms.spin.tikitak.service.auth;

import kusitms.spin.tikitak.global.time.KstDateProvider;
import kusitms.spin.tikitak.repository.auth.RefreshTokenRepository;
import kusitms.spin.tikitak.support.UnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class RefreshTokenCleanupBatchServiceTest extends UnitTest {

	@Mock
	private RefreshTokenRepository refreshTokenRepository;

	@InjectMocks
	private RefreshTokenCleanupBatchService refreshTokenCleanupBatchService;

	@Test
	@DisplayName("만료 토큰 정리 배치는 deleteExpired를 한 번 호출한다")
	void deleteExpiredRefreshTokensCallsDeleteExpiredOnce() {
		given(refreshTokenRepository.deleteExpired(any(LocalDateTime.class))).willReturn(3);

		LocalDateTime before = LocalDateTime.now();

		refreshTokenCleanupBatchService.deleteExpiredRefreshTokens();

		LocalDateTime after = LocalDateTime.now();
		ArgumentCaptor<LocalDateTime> nowCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
		verify(refreshTokenRepository, times(1)).deleteExpired(nowCaptor.capture());
		assertThat(nowCaptor.getValue()).isBetween(before, after);
		verifyNoMoreInteractions(refreshTokenRepository);
	}

	@Test
	@DisplayName("만료 토큰 정리 배치는 매일 04:45 KST에 실행된다")
	void deleteExpiredRefreshTokensIsScheduledDailyAt0445Kst() throws NoSuchMethodException {
		Scheduled scheduled = RefreshTokenCleanupBatchService.class
				.getMethod("deleteExpiredRefreshTokens")
				.getAnnotation(Scheduled.class);

		assertThat(scheduled).isNotNull();
		assertThat(scheduled.cron()).isEqualTo("0 45 4 * * *");
		assertThat(scheduled.zone()).isEqualTo("Asia/Seoul");
		assertThat(scheduled.zone()).isEqualTo(KstDateProvider.KST_ZONE_ID);
	}
}
