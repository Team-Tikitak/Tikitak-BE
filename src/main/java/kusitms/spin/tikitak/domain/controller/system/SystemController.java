package kusitms.spin.tikitak.domain.controller.system;

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
public class SystemController {

    @GetMapping("/health")
    public ResponseEntity<CommonResponse<HealthResponse>> healthCheck() {
        HealthResponse data = HealthResponse.up();
        return ResponseEntity.ok(CommonResponse.success(data));
    }
}