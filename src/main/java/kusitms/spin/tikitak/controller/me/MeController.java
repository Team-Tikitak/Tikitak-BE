package kusitms.spin.tikitak.controller.me;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kusitms.spin.tikitak.global.dto.CommonResponse;
import kusitms.spin.tikitak.global.security.CurrentMemberId;
import kusitms.spin.tikitak.service.me.MeService;
import kusitms.spin.tikitak.service.me.dto.MeRequestDTO;
import kusitms.spin.tikitak.service.me.dto.MeResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Me", description = "내 계정 관리 API")
public class MeController {

	private final MeService meService;

	@Operation(
			summary = "내 계정 정보 조회",
			description = "현재 로그인한 사용자의 기본 정보와 활성 팀 정보를 조회합니다.",
			security = @SecurityRequirement(name = "bearerAuth")
	)
	@GetMapping("/api/v1/me")
	public CommonResponse<MeResponseDTO.MeProfileResponseDTO> getMyProfile(
			@Parameter(hidden = true) @CurrentMemberId Long memberId
	) {
		return CommonResponse.success(meService.getMyProfile(memberId));
	}

	@Operation(
			summary = "내 참여 팀 목록 조회",
			description = "현재 사용자가 ACTIVE 상태로 참여 중인 ACTIVE 팀 목록을 조회합니다.",
			security = @SecurityRequirement(name = "bearerAuth")
	)
	@GetMapping("/api/v1/me/teams")
	public CommonResponse<MeResponseDTO.TeamListResponseDTO> getMyTeams(
			@Parameter(hidden = true) @CurrentMemberId Long memberId
	) {
		return CommonResponse.success(meService.getMyTeams(memberId));
	}

	@Operation(
			summary = "활성 팀 변경",
			description = "현재 사용자의 활성 팀을 변경합니다.",
			security = @SecurityRequirement(name = "bearerAuth")
	)
	@PatchMapping("/api/v1/me/active-team")
	public CommonResponse<MeResponseDTO.ActiveTeamUpdateResponseDTO> updateActiveTeam(
			@Parameter(hidden = true) @CurrentMemberId Long memberId,
			@Valid @RequestBody MeRequestDTO.ActiveTeamUpdateRequestDTO request
	) {
		return CommonResponse.success(meService.updateActiveTeam(memberId, request));
	}

	@Operation(
			summary = "약관 동의 현황 조회",
			description = "현재 사용자의 필수 약관 동의 상태를 조회합니다.",
			security = @SecurityRequirement(name = "bearerAuth")
	)
	@GetMapping("/api/v1/me/agreements")
	public CommonResponse<MeResponseDTO.AgreementResponseDTO> getAgreements(
			@Parameter(hidden = true) @CurrentMemberId Long memberId
	) {
		return CommonResponse.success(meService.getAgreements(memberId));
	}

	@Operation(
			summary = "약관 동의 저장",
			description = "현재 사용자의 필수 약관 동의 정보를 저장합니다.",
			security = @SecurityRequirement(name = "bearerAuth")
	)
	@PutMapping("/api/v1/me/agreements")
	public CommonResponse<MeResponseDTO.AgreementResponseDTO> updateAgreements(
			@Parameter(hidden = true) @CurrentMemberId Long memberId,
			@Valid @RequestBody MeRequestDTO.AgreementUpdateRequestDTO request
	) {
		return CommonResponse.success(meService.updateAgreements(memberId, request));
	}

	@Operation(
			summary = "온보딩 프로필 등록",
			description = "현재 사용자의 온보딩 완료 여부와 기본 프로필 캐릭터를 저장합니다.",
			security = @SecurityRequirement(name = "bearerAuth")
	)
	@PatchMapping("/api/v1/me/onboarding")
	public CommonResponse<MeResponseDTO.OnboardingUpdateResponseDTO> updateOnboarding(
			@Parameter(hidden = true) @CurrentMemberId Long memberId,
			@Valid @RequestBody MeRequestDTO.OnboardingUpdateRequestDTO request
	) {
		return CommonResponse.success(meService.updateOnboarding(memberId, request));
	}

	@Operation(
			summary = "회원탈퇴",
			description = "현재 로그인한 사용자의 계정을 탈퇴 처리하고 발급된 refresh token을 모두 무효화합니다.",
			security = @SecurityRequirement(name = "bearerAuth")
	)
	@DeleteMapping("/api/v1/me")
	public ResponseEntity<CommonResponse<Void>> withdraw(
			@Parameter(hidden = true) @CurrentMemberId Long memberId
	) {
		meService.withdraw(memberId);
		return ResponseEntity.ok(CommonResponse.success(null));
	}
}
