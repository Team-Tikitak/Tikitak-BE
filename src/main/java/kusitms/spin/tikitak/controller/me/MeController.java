package kusitms.spin.tikitak.controller.me;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import kusitms.spin.tikitak.global.dto.CommonResponse;
import kusitms.spin.tikitak.global.security.CurrentMemberId;
import kusitms.spin.tikitak.service.me.MeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Me", description = "내 계정 관리 API")
public class MeController {

	private final MeService meService;

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
