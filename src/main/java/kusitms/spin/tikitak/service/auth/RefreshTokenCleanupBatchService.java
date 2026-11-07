package kusitms.spin.tikitak.service.auth;

import kusitms.spin.tikitak.global.time.KstDateProvider;
import kusitms.spin.tikitak.repository.auth.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class RefreshTokenCleanupBatchService {

	private final RefreshTokenRepository refreshTokenRepository;

	/**
	 * 만료된 refresh token 행을 삭제한다. revoke됐지만 만료 전인 행은 재사용 탐지에 쓰이므로 남긴다.
	 * 같은 DELETE는 여러 번 돌아도 결과가 같으므로 분산 락을 두지 않는다.
	 */
	@Scheduled(cron = "0 45 4 * * *", zone = KstDateProvider.KST_ZONE_ID)
	@Transactional
	public void deleteExpiredRefreshTokens() {
		int deleted = refreshTokenRepository.deleteExpired(LocalDateTime.now());
		log.info("Deleted {} expired refresh tokens", deleted);
	}
}
