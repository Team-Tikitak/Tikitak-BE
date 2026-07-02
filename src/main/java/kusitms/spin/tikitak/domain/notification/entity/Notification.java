package kusitms.spin.tikitak.domain.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import kusitms.spin.tikitak.domain.member.entity.Member;
import kusitms.spin.tikitak.domain.notification.enums.NotificationType;
import kusitms.spin.tikitak.service.notification.dto.NotificationPayload;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "notification")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "member_id", nullable = false)
	private Member member;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 50)
	private NotificationType type;

	@Column(nullable = false, length = 100)
	private String title;

	@Column(nullable = false, length = 255)
	private String body;

	@Column(name = "team_id")
	private Long teamId;

	@Column(name = "feed_id")
	private Long feedId;

	@Column(nullable = false)
	private boolean isRead;

	private LocalDateTime readAt;

	@Column(nullable = false)
	private LocalDateTime createdAt;

	public static Notification create(Member member, NotificationPayload payload) {
		return Notification.builder()
				.member(member)
				.type(payload.getType())
				.title(payload.getTitle())
				.body(payload.getBody())
				.teamId(parseLong(payload.getData().get("teamId")))
				.feedId(parseLong(payload.getData().get("feedId")))
				.isRead(false)
				.createdAt(LocalDateTime.now())
				.build();
	}

	public void markAsRead() {
		if (isRead) {
			return;
		}
		this.isRead = true;
		this.readAt = LocalDateTime.now();
	}

	private static Long parseLong(String value) {
		return value == null ? null : Long.valueOf(value);
	}
}
