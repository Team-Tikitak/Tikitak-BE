package kusitms.spin.tikitak.service.auth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import kusitms.spin.tikitak.domain.auth.RefreshToken;
import kusitms.spin.tikitak.domain.member.entity.Member;
import kusitms.spin.tikitak.domain.member.enums.MemberStatus;
import kusitms.spin.tikitak.global.config.AuthProperties;
import kusitms.spin.tikitak.global.exception.BusinessException;
import kusitms.spin.tikitak.global.exception.ErrorCode;
import kusitms.spin.tikitak.repository.auth.RefreshTokenRepository;
import kusitms.spin.tikitak.repository.member.MemberRepository;
import kusitms.spin.tikitak.service.auth.dto.TokenResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class TokenService {

	private static final String TOKEN_TYPE = "Bearer";
	private static final String HMAC_SHA256 = "HmacSHA256";

	private final AuthProperties authProperties;
	private final ObjectMapper objectMapper;
	private final MemberRepository memberRepository;
	private final RefreshTokenRepository refreshTokenRepository;

	@Transactional
	public TokenResponse issueToken(Long memberId) {
		AuthProperties.Jwt jwt = authProperties.jwt();
		String accessToken = createToken(memberId, "access", jwt.accessTokenExpiresIn());
		String refreshToken = createToken(memberId, "refresh", jwt.refreshTokenExpiresIn());
		Member member = memberRepository.findByIdAndStatus(memberId, MemberStatus.ACTIVE)
				.orElseThrow(() -> new BusinessException(ErrorCode.AUTH007));
		refreshTokenRepository.save(RefreshToken.issue(
				member,
				hash(refreshToken),
				LocalDateTime.now().plusSeconds(jwt.refreshTokenExpiresIn())
		));

		return new TokenResponse(
				accessToken,
				refreshToken,
				TOKEN_TYPE,
				jwt.accessTokenExpiresIn()
		);
	}

	@Transactional
	public TokenResponse reissueToken(String refreshToken) {
		Long memberId = validateRefreshToken(refreshToken);
		revokeRefreshToken(refreshToken);
		return issueToken(memberId);
	}

	@Transactional
	public void revokeRefreshToken(String refreshToken) {
		if (refreshToken == null || refreshToken.isBlank()) {
			return;
		}
		refreshTokenRepository.findByTokenHash(hash(refreshToken))
				.ifPresent(RefreshToken::revoke);
	}

	@Transactional
	public void revokeAllRefreshTokens(Long memberId) {
		refreshTokenRepository.findAllByMemberIdAndRevokedFalse(memberId)
				.forEach(RefreshToken::revoke);
	}

	public Long getMemberIdFromAccessToken(String accessToken) {
		return validateAccessToken(accessToken);
	}

	private Long validateRefreshToken(String refreshToken) {
		if (refreshToken == null || refreshToken.isBlank()) {
			throw new BusinessException(ErrorCode.AUTH006);
		}

		try {
			String[] parts = refreshToken.split("\\.");
			if (parts.length != 3) {
				throw new BusinessException(ErrorCode.AUTH007);
			}

			String unsignedToken = parts[0] + "." + parts[1];
			if (!Objects.equals(sign(unsignedToken), parts[2])) {
				throw new BusinessException(ErrorCode.AUTH007);
			}

			Map<String, Object> payload = objectMapper.readValue(
					Base64.getUrlDecoder().decode(parts[1]),
					new TypeReference<>() {
					}
			);

			if (!Objects.equals(payload.get("type"), "refresh")) {
				throw new BusinessException(ErrorCode.AUTH007);
			}

			long expiresAt = ((Number) payload.get("exp")).longValue();
			if (Instant.now().getEpochSecond() >= expiresAt) {
				throw new BusinessException(ErrorCode.AUTH007);
			}

			RefreshToken savedToken = refreshTokenRepository.findByTokenHash(hash(refreshToken))
					.orElseThrow(() -> new BusinessException(ErrorCode.AUTH007));
			if (!savedToken.isUsable(LocalDateTime.now())) {
				throw new BusinessException(ErrorCode.AUTH007);
			}

			Long memberId = Long.valueOf(String.valueOf(payload.get("sub")));
			memberRepository.findByIdAndStatus(memberId, MemberStatus.ACTIVE)
					.orElseThrow(() -> new BusinessException(ErrorCode.AUTH007));
			return memberId;
		} catch (BusinessException e) {
			throw e;
		} catch (Exception e) {
			throw new BusinessException(ErrorCode.AUTH007);
		}
	}

	private Long validateAccessToken(String accessToken) {
		if (accessToken == null || accessToken.isBlank()) {
			throw new BusinessException(ErrorCode.AUTH009);
		}

		try {
			String[] parts = accessToken.split("\\.");
			if (parts.length != 3) {
				throw new BusinessException(ErrorCode.AUTH009);
			}

			String unsignedToken = parts[0] + "." + parts[1];
			if (!Objects.equals(sign(unsignedToken), parts[2])) {
				throw new BusinessException(ErrorCode.AUTH009);
			}

			Map<String, Object> payload = objectMapper.readValue(
					Base64.getUrlDecoder().decode(parts[1]),
					new TypeReference<>() {
					}
			);

			if (!Objects.equals(payload.get("type"), "access")) {
				throw new BusinessException(ErrorCode.AUTH009);
			}

			long expiresAt = ((Number) payload.get("exp")).longValue();
			if (Instant.now().getEpochSecond() >= expiresAt) {
				throw new BusinessException(ErrorCode.AUTH009);
			}

			Long memberId = Long.valueOf(String.valueOf(payload.get("sub")));
			memberRepository.findByIdAndStatus(memberId, MemberStatus.ACTIVE)
					.orElseThrow(() -> new BusinessException(ErrorCode.AUTH009));
			return memberId;
		} catch (BusinessException e) {
			throw e;
		} catch (Exception e) {
			throw new BusinessException(ErrorCode.AUTH009);
		}
	}

	private String createToken(Long memberId, String tokenType, long expiresInSeconds) {
		Instant now = Instant.now();
		Map<String, Object> header = new LinkedHashMap<>();
		header.put("alg", "HS256");
		header.put("typ", "JWT");

		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("sub", String.valueOf(memberId));
		payload.put("type", tokenType);
		payload.put("iat", now.getEpochSecond());
		payload.put("exp", now.plusSeconds(expiresInSeconds).getEpochSecond());

		String unsignedToken = base64Url(toJson(header)) + "." + base64Url(toJson(payload));
		return unsignedToken + "." + sign(unsignedToken);
	}

	private String toJson(Map<String, Object> value) {
		try {
			return objectMapper.writeValueAsString(value);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Failed to create token payload.", e);
		}
	}

	private String sign(String value) {
		try {
			Mac mac = Mac.getInstance(HMAC_SHA256);
			mac.init(new SecretKeySpec(authProperties.jwt().secret().getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
			return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
		} catch (Exception e) {
			throw new IllegalStateException("Failed to sign token.", e);
		}
	}

	private String base64Url(String value) {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
	}

	private String hash(String value) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
			StringBuilder builder = new StringBuilder(hash.length * 2);
			for (byte b : hash) {
				builder.append(String.format("%02x", b));
			}
			return builder.toString();
		} catch (Exception e) {
			throw new IllegalStateException("Failed to hash token.", e);
		}
	}
}
