package kusitms.spin.tikitak.controller.system;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import kusitms.spin.tikitak.domain.dto.system.HealthResponse;
import kusitms.spin.tikitak.global.dto.CommonResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/system")
@Tag(name = "System", description = "시스템 상태 확인 API")
public class SystemController {

    @Operation(summary = "서버 상태 확인", description = "서버가 정상적으로 실행 중인지 확인합니다.")
    @GetMapping("/health")
    public ResponseEntity<CommonResponse<HealthResponse>> healthCheck() {
        HealthResponse data = HealthResponse.up();
        return ResponseEntity.ok(CommonResponse.success(data));
    }
}
