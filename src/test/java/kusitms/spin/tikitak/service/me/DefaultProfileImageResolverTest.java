package kusitms.spin.tikitak.service.me;

import kusitms.spin.tikitak.domain.member.enums.ProfileCharacterType;
import kusitms.spin.tikitak.global.config.ImageOptimizationProperties;
import kusitms.spin.tikitak.global.config.R2Properties;
import kusitms.spin.tikitak.global.exception.BusinessException;
import kusitms.spin.tikitak.global.exception.ErrorCode;
import kusitms.spin.tikitak.service.media.ImageUrlResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultProfileImageResolverTest {

	@Test
	@DisplayName("기본 프로필 캐릭터 타입을 R2 공개 URL로 변환한다")
	void resolveBuildsDefaultProfileImageUrl() {
		R2Properties r2Properties = new R2Properties();
		r2Properties.setPublicBaseUrl("https://media.tikitak.xyz/");
		DefaultProfileImageResolver resolver = new DefaultProfileImageResolver(
				r2Properties,
				new ImageUrlResolver(r2Properties, new ImageOptimizationProperties())
		);

		String imageUrl = resolver.resolve(ProfileCharacterType.TAK_SPARK);

		assertThat(imageUrl).isEqualTo(
				"https://media.tikitak.xyz/default-profiles/tak-spark.png?preset=profile_avatar");
	}

	@Test
	@DisplayName("R2 공개 URL 설정이 없으면 온보딩 프로필 저장 실패 예외를 던진다")
	void resolveThrowsWhenPublicBaseUrlMissing() {
		R2Properties r2Properties = new R2Properties();
		DefaultProfileImageResolver resolver = new DefaultProfileImageResolver(
				r2Properties,
				new ImageUrlResolver(r2Properties, new ImageOptimizationProperties())
		);

		assertThatThrownBy(() -> resolver.resolve(ProfileCharacterType.TAK_SPARK))
				.isInstanceOfSatisfying(BusinessException.class, exception ->
						assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ME011));
	}
}
