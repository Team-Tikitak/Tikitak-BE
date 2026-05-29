package kusitms.spin.tikitak.service.feed;

import kusitms.spin.tikitak.global.exception.BusinessException;
import kusitms.spin.tikitak.global.exception.ErrorCode;

import java.math.BigDecimal;

final class FeedCommentValidator {

	static final int DEFAULT_PAGE_SIZE = 20;
	static final int MAX_PAGE_SIZE = 50;
	private static final int MAX_CONTENT_LENGTH = 200;
	private static final int MAX_POSITION_SCALE = 6;
	private static final BigDecimal MIN_POSITION = BigDecimal.ZERO;
	private static final BigDecimal MAX_POSITION = BigDecimal.ONE;

	private FeedCommentValidator() {
	}

	static String normalizeContent(String content) {
		if (content == null || content.isBlank()) {
			throw new BusinessException(ErrorCode.COMMENT002);
		}
		String normalized = content.trim();
		if (normalized.length() > MAX_CONTENT_LENGTH) {
			throw new BusinessException(ErrorCode.COMMENT003);
		}
		return normalized;
	}

	static void validatePositionPair(BigDecimal positionX, BigDecimal positionY) {
		if (!isValidPosition(positionX) || !isValidPosition(positionY)) {
			throw new BusinessException(ErrorCode.COMMENT010);
		}
	}

	static int normalizePageSize(Integer size) {
		if (size == null || size < 1) {
			return DEFAULT_PAGE_SIZE;
		}
		return Math.min(size, MAX_PAGE_SIZE);
	}

	private static boolean isValidPosition(BigDecimal position) {
		return position != null
				&& position.compareTo(MIN_POSITION) >= 0
				&& position.compareTo(MAX_POSITION) <= 0
				&& position.scale() <= MAX_POSITION_SCALE;
	}
}
