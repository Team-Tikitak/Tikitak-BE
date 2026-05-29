package kusitms.spin.tikitak.global.time;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

@Component
public class KstDateProvider {

	public static final ZoneId KST = ZoneId.of("Asia/Seoul");

	private final Clock clock;

	public KstDateProvider() {
		this(Clock.system(KST));
	}

	public KstDateProvider(Clock clock) {
		this.clock = clock;
	}

	public LocalDate today() {
		return LocalDate.now(clock.withZone(KST));
	}

	public LocalDate toKstDate(LocalDateTime dateTime) {
		return ZonedDateTime.of(dateTime, KST).toLocalDate();
	}
}
