package kusitms.spin.tikitak.global.time;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;

@Component
public class KstDateProvider {

	public static final String KST_ZONE_ID = "Asia/Seoul";
	public static final ZoneId KST = ZoneId.of(KST_ZONE_ID);

	private final Clock clock;
	private final ZoneId systemZone;

	@Autowired
	public KstDateProvider() {
		this(Clock.system(KST), ZoneId.systemDefault());
	}

	public KstDateProvider(Clock clock, ZoneId systemZone) {
		this.clock = clock;
		this.systemZone = systemZone;
	}

	public LocalDate today() {
		return LocalDate.now(clock.withZone(KST));
	}

	public YearMonth currentMonth() {
		return YearMonth.now(clock.withZone(KST));
	}

	/**
	 * KST 기준 해당 월 1일 00:00을 시스템 zone 벽시계 시각으로 바꿔 반환한다.
	 */
	public LocalDateTime startOfMonth(YearMonth month) {
		return month.atDay(1)
				.atStartOfDay(KST)
				.withZoneSameInstant(systemZone)
				.toLocalDateTime();
	}

	/**
	 * 시스템 zone 벽시계로 저장된 시각을 KST 날짜로 바꾼다.
	 */
	public LocalDate toKstDate(LocalDateTime dateTime) {
		return ZonedDateTime.of(dateTime, systemZone)
				.withZoneSameInstant(KST)
				.toLocalDate();
	}
}
