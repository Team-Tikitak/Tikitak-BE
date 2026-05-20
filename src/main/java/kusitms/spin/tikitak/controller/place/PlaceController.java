package kusitms.spin.tikitak.controller.place;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import kusitms.spin.tikitak.global.dto.CommonResponse;
import kusitms.spin.tikitak.service.place.PlaceService;
import kusitms.spin.tikitak.service.place.dto.PlaceSearchResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "장소 컨트롤러")
@RestController
@RequestMapping("/api/v1/places")
@RequiredArgsConstructor
@Validated
public class PlaceController {

    private final PlaceService placeService;

    @GetMapping("/search")
    @Operation(summary = "장소 검색 API", description = "카카오맵 키워드 검색으로 장소를 조회합니다.")
    public CommonResponse<PlaceSearchResponseDTO.PlaceSearchResult> searchPlaces(
            @Parameter(description = "검색 키워드", required = true)
            @RequestParam @NotBlank String query,

            @Parameter(description = "중심 좌표 경도 (latitude, longitude 함께 입력 시 거리순 정렬 가능)")
            @RequestParam(required = false) String longitude,

            @Parameter(description = "중심 좌표 위도 (latitude, longitude 함께 입력 시 거리순 정렬 가능)")
            @RequestParam(required = false) String latitude,

            @Parameter(description = "중심 좌표로부터 반경 (미터, 최대 20000)")
            @RequestParam(required = false) @Min(1) @Max(20000) Integer radius,

            @Parameter(description = "페이지 번호 (1~45, 기본값 1)")
            @RequestParam(required = false, defaultValue = "1") @Min(1) @Max(45) Integer page,

            @Parameter(description = "페이지당 결과 수 (1~15, 기본값 15)")
            @RequestParam(required = false, defaultValue = "15") @Min(1) @Max(15) Integer size
    ) {
        PlaceSearchResponseDTO.PlaceSearchResult result = placeService.searchPlaces(query, longitude, latitude, radius, page, size);
        return CommonResponse.success(result);
    }
}
