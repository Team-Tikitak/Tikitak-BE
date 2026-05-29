package kusitms.spin.tikitak.service.feed;

import kusitms.spin.tikitak.global.exception.BusinessException;
import kusitms.spin.tikitak.global.exception.ErrorCode;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FeedCommentValidatorPropertyTest {

	@Property
	void acceptsPositionsWithinImageBounds(@ForAll("validPositions") BigDecimal x, @ForAll("validPositions") BigDecimal y) {
		FeedCommentValidator.validatePositionPair(x, y);
	}

	@Property
	void rejectsPositionOutsideImageBounds(@ForAll("invalidPositions") BigDecimal invalidPosition) {
		assertThatThrownBy(() -> FeedCommentValidator.validatePositionPair(invalidPosition, BigDecimal.valueOf(0.5)))
				.isInstanceOfSatisfying(BusinessException.class, exception ->
						assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.COMMENT010));
	}

	@Provide
	Arbitrary<BigDecimal> validPositions() {
		return Arbitraries.bigDecimals()
				.between(BigDecimal.ZERO, BigDecimal.ONE)
				.ofScale(6);
	}

	@Provide
	Arbitrary<BigDecimal> invalidPositions() {
		Arbitrary<BigDecimal> negative = Arbitraries.bigDecimals()
				.between(BigDecimal.valueOf(-10), new BigDecimal("-0.000001"))
				.ofScale(6);
		Arbitrary<BigDecimal> tooLarge = Arbitraries.bigDecimals()
				.between(new BigDecimal("1.000001"), BigDecimal.TEN)
				.ofScale(6);
		return Arbitraries.oneOf(negative, tooLarge);
	}
}
