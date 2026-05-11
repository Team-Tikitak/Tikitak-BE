package kusitms.spin.tikitak.domain.media.entity;

import jakarta.persistence.*;
import kusitms.spin.tikitak.domain.media.enums.MediaStatus;
import kusitms.spin.tikitak.domain.media.enums.MediaPurpose;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "media")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class Media {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private MediaPurpose purpose;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MediaStatus status;

    @Column(nullable = false, length = 255)
    private String fileName;

    @Column(nullable = false, length = 100)
    private String contentType;

    @Column(nullable = false)
    private Long size;

    @Column(columnDefinition = "text")
    private String url;

    @Column
    private LocalDateTime uploadedAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Column
    private Long teamId;

    @Column(nullable = false)
    private Long memberId;

    public void updateStatus(MediaStatus status) {
        this.status = status;
        this.updatedAt = LocalDateTime.now();
        if (status == MediaStatus.UPLOADED) {
            this.uploadedAt = LocalDateTime.now();
        }
    }

    public void updateUrl(String url) {
        this.url = url;
        this.updatedAt = LocalDateTime.now();
    }
}