package kusitms.spin.tikitak.global.time;

import kusitms.spin.tikitak.support.UnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class KstDateProviderTest extends UnitTest {

	private static final ZoneId UTC = ZoneOffset.UTC;
	private static final ZoneId KST = ZoneId.of("Asia/Seoul");
	private static final Clock ANY_CLOCK = Clock.fixed(Instant.parse("2026-03-04T03:00:00Z"), UTC);

	@Test
	@DisplayName("KST_ZONE_ID 상수는 Asia/Seoul이고 KST와 같은 zone이다")
	void kstZoneIdConstant() {
		assertThat(KstDateProvider.KST_ZONE_ID).isEqualTo("Asia/Seoul");
		assertThat(KstDateProvider.KST).isEqualTo(ZoneId.of(KstDateProvider.KST_ZONE_ID));
	}

	@Test
	@DisplayName("시스템 zone이 UTC여도 KST 11월 30일 23:59:59이면 이번 달은 2026-11이다")
	void currentMonthJustBeforeKstMonthBoundary() {
		KstDateProvider provider = new KstDateProvider(
				Clock.fixed(Instant.parse("2026-11-30T14:59:59Z"), UTC), UTC);

		assertThat(provider.currentMonth()).isEqualTo(YearMonth.of(2026, 11));
	}

	@Test
	@DisplayName("시스템 zone이 UTC여도 KST 12월 1일 00:00이면 이번 달은 2026-12이다")
	void currentMonthAtKstMonthBoundary() {
		KstDateProvider provider = new KstDateProvider(
				Clock.fixed(Instant.parse("2026-11-30T15:00:00Z"), UTC), UTC);

		assertThat(provider.currentMonth()).isEqualTo(YearMonth.of(2026, 12));
	}

	@Test
	@DisplayName("시스템 zone이 UTC이면 KST 월 시작을 UTC 벽시계(전날 15:00)로 변환한다")
	void startOfMonthInUtcSystemZone() {
		KstDateProvider provider = new KstDateProvider(ANY_CLOCK, UTC);

		assertThat(provider.startOfMonth(YearMonth.of(2026, 12)))
				.isEqualTo(LocalDateTime.of(2026, 11, 30, 15, 0));
	}

	@Test
	@DisplayName("시스템 zone이 UTC이면 저장 시각을 UTC로 해석해 KST 날짜를 구한다")
	void toKstDateInUtcSystemZone() {
		KstDateProvider provider = new KstDateProvider(ANY_CLOCK, UTC);

		assertThat(provider.toKstDate(LocalDateTime.of(2026, 3, 3, 15, 0)))
				.isEqualTo(LocalDate.of(2026, 3, 4));
		assertThat(provider.toKstDate(LocalDateTime.of(2026, 3, 3, 14, 59, 59)))
				.isEqualTo(LocalDate.of(2026, 3, 3));
	}

	@Test
	@DisplayName("시스템 zone이 KST이면 월 시작은 입력 월 1일 00:00 그대로다")
	void startOfMonthInKstSystemZone() {
		KstDateProvider provider = new KstDateProvider(ANY_CLOCK, KST);

		assertThat(provider.startOfMonth(YearMonth.of(2026, 12)))
				.isEqualTo(LocalDateTime.of(2026, 12, 1, 0, 0));
	}

	@Test
	@DisplayName("시스템 zone이 KST이면 저장 시각의 날짜를 그대로 KST 날짜로 쓴다")
	void toKstDateInKstSystemZone() {
		KstDateProvider provider = new KstDateProvider(ANY_CLOCK, KST);

		assertThat(provider.toKstDate(LocalDateTime.of(2026, 3, 3, 15, 0)))
				.isEqualTo(LocalDate.of(2026, 3, 3));
		assertThat(provider.toKstDate(LocalDateTime.of(2026, 3, 3, 23, 59, 59)))
				.isEqualTo(LocalDate.of(2026, 3, 3));
		assertThat(provider.toKstDate(LocalDateTime.of(2026, 3, 4, 0, 0)))
				.isEqualTo(LocalDate.of(2026, 3, 4));
	}

	@Test
	@DisplayName("Spring은 생성자가 여러 개여도 기본 생성자로 빈을 만든다")
	void springCreatesBeanWithDefaultConstructor() {
		new ApplicationContextRunner()
				.withUserConfiguration(KstDateProvider.class)
				.run(context -> {
					assertThat(context).hasNotFailed();
					assertThat(context).hasSingleBean(KstDateProvider.class);
				});
	}
}
