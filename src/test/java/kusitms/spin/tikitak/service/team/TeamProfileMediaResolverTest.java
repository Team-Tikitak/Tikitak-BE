package kusitms.spin.tikitak.service.team;

import kusitms.spin.tikitak.domain.media.entity.Media;
import kusitms.spin.tikitak.domain.media.enums.MediaPurpose;
import kusitms.spin.tikitak.domain.media.enums.MediaStatus;
import kusitms.spin.tikitak.global.exception.BusinessException;
import kusitms.spin.tikitak.global.exception.ErrorCode;
import kusitms.spin.tikitak.repository.media.MediaRepository;
import kusitms.spin.tikitak.support.UnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.Optional;
import java.util.UUID;

import static kusitms.spin.tikitak.support.fixture.MediaFixture.media;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TeamProfileMediaResolverTest extends UnitTest {

	private static final Long MEMBER_ID = 1L;
	private static final Long OTHER_MEMBER_ID = 2L;
	private static final UUID MEDIA_PUBLIC_ID = UUID.fromString("00000000-0000-0000-0000-000000000194");

	@Mock
	private MediaRepository mediaRepository;

	@InjectMocks
	private TeamProfileMediaResolver teamProfileMediaResolver;

	@Test
	@DisplayName("mediaPublicId가 null이면 null을 반환하고 media를 조회하지 않는다")
	void resolveReturnsNullWhenMediaPublicIdIsNull() {
		Media result = teamProfileMediaResolver.resolve(MEMBER_ID, null);

		assertThat(result).isNull();
		verify(mediaRepository, never()).findByPublicIdForUpdate(any());
	}

	@Test
	@DisplayName("media가 없으면 MEDIA018을 던진다")
	void resolveThrowsWhenMediaNotFound() {
		when(mediaRepository.findByPublicIdForUpdate(MEDIA_PUBLIC_ID)).thenReturn(Optional.empty());

		assertThatMedia018(() -> teamProfileMediaResolver.resolve(MEMBER_ID, MEDIA_PUBLIC_ID));
	}

	@Test
	@DisplayName("다른 회원 소유 media면 MEDIA018을 던지고 상태를 바꾸지 않는다")
	void resolveThrowsWhenMediaOwnedByOtherMember() {
		Media requestedMedia = media(
				100L, OTHER_MEMBER_ID, MEDIA_PUBLIC_ID, MediaPurpose.PROFILE_IMAGE, MediaStatus.UPLOADED);
		when(mediaRepository.findByPublicIdForUpdate(MEDIA_PUBLIC_ID)).thenReturn(Optional.of(requestedMedia));

		assertThatMedia018(() -> teamProfileMediaResolver.resolve(MEMBER_ID, MEDIA_PUBLIC_ID));
		assertThat(requestedMedia.getStatus()).isEqualTo(MediaStatus.UPLOADED);
	}

	@ParameterizedTest
	@EnumSource(value = MediaPurpose.class, mode = EnumSource.Mode.EXCLUDE, names = "PROFILE_IMAGE")
	@DisplayName("purpose가 PROFILE_IMAGE가 아니면 MEDIA018을 던지고 상태를 바꾸지 않는다")
	void resolveThrowsWhenPurposeIsNotProfileImage(MediaPurpose purpose) {
		Media requestedMedia = media(100L, MEMBER_ID, MEDIA_PUBLIC_ID, purpose, MediaStatus.UPLOADED);
		when(mediaRepository.findByPublicIdForUpdate(MEDIA_PUBLIC_ID)).thenReturn(Optional.of(requestedMedia));

		assertThatMedia018(() -> teamProfileMediaResolver.resolve(MEMBER_ID, MEDIA_PUBLIC_ID));
		assertThat(requestedMedia.getStatus()).isEqualTo(MediaStatus.UPLOADED);
	}

	@ParameterizedTest
	@EnumSource(value = MediaStatus.class, names = {"PENDING", "USED", "DELETED"})
	@DisplayName("UPLOADED가 아닌 media면 MEDIA018을 던지고 상태를 바꾸지 않는다")
	void resolveThrowsWhenStatusIsNotUploaded(MediaStatus status) {
		Media requestedMedia = media(100L, MEMBER_ID, MEDIA_PUBLIC_ID, MediaPurpose.PROFILE_IMAGE, status);
		when(mediaRepository.findByPublicIdForUpdate(MEDIA_PUBLIC_ID)).thenReturn(Optional.of(requestedMedia));

		assertThatMedia018(() -> teamProfileMediaResolver.resolve(MEMBER_ID, MEDIA_PUBLIC_ID));
		assertThat(requestedMedia.getStatus()).isEqualTo(status);
	}

	@Test
	@DisplayName("본인 소유의 UPLOADED 프로필 media면 USED로 바꾸고 같은 객체를 반환한다")
	void resolveMarksMediaUsedAndReturnsIt() {
		Media requestedMedia = media(
				100L, MEMBER_ID, MEDIA_PUBLIC_ID, MediaPurpose.PROFILE_IMAGE, MediaStatus.UPLOADED);
		when(mediaRepository.findByPublicIdForUpdate(MEDIA_PUBLIC_ID)).thenReturn(Optional.of(requestedMedia));

		Media result = teamProfileMediaResolver.resolve(MEMBER_ID, MEDIA_PUBLIC_ID);

		assertThat(result).isSameAs(requestedMedia);
		assertThat(result.getStatus()).isEqualTo(MediaStatus.USED);
	}

	private void assertThatMedia018(Runnable call) {
		assertThatThrownBy(call::run)
				.isInstanceOf(BusinessException.class)
				.satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.MEDIA018));
	}
}
