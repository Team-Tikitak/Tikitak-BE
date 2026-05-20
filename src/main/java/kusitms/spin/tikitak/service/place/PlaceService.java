package kusitms.spin.tikitak.service.place;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import kusitms.spin.tikitak.global.config.KakaoMapProperties;
import kusitms.spin.tikitak.global.exception.BusinessException;
import kusitms.spin.tikitak.global.exception.ErrorCode;
import kusitms.spin.tikitak.service.place.dto.PlaceSearchResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlaceService {

    private static final String KAKAO_KEYWORD_SEARCH_URI = "https://dapi.kakao.com/v2/local/search/keyword.json";

    private final KakaoMapProperties kakaoMapProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    public PlaceSearchResponseDTO.PlaceSearchResult searchPlaces(
            String query,
            String longitude,
            String latitude,
            Integer radius,
            Integer page,
            Integer size
    ) {
        URI uri = buildUri(query, longitude, latitude, radius, page, size);

        HttpRequest request = HttpRequest.newBuilder(uri)
                .header("Authorization", "KakaoAK " + kakaoMapProperties.restApiKey())
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();

        KakaoKeywordSearchResponse kakaoResponse = send(request);
        return toResult(kakaoResponse);
    }

    private URI buildUri(String query, String longitude, String latitude, Integer radius, Integer page, Integer size) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(KAKAO_KEYWORD_SEARCH_URI)
                .queryParam("query", query);

        if (longitude != null && latitude != null) {
            builder.queryParam("x", longitude).queryParam("y", latitude);
        }
        if (radius != null) {
            builder.queryParam("radius", radius);
        }
        if (page != null) {
            builder.queryParam("page", page);
        }
        if (size != null) {
            builder.queryParam("size", size);
        }

        return builder.build().encode().toUri();
    }

    private KakaoKeywordSearchResponse send(HttpRequest request) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("Kakao Map API request failed. status={}, body={}", response.statusCode(), truncate(response.body()));
                throw new BusinessException(ErrorCode.PLACE001);
            }
            return objectMapper.readValue(response.body(), KakaoKeywordSearchResponse.class);
        } catch (BusinessException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.PLACE001);
        } catch (IOException e) {
            log.warn("Kakao Map API response parsing failed. reason={}", e.getMessage());
            throw new BusinessException(ErrorCode.PLACE001);
        }
    }

    private PlaceSearchResponseDTO.PlaceSearchResult toResult(KakaoKeywordSearchResponse kakaoResponse) {
        KakaoMeta meta = kakaoResponse.meta();

        List<PlaceSearchResponseDTO.Place> places = kakaoResponse.documents().stream()
                .map(doc -> PlaceSearchResponseDTO.Place.builder()
                        .kakaoPlaceId(doc.id())
                        .name(doc.placeName())
                        .category(doc.categoryName())
                        .address(doc.addressName())
                        .roadAddress(doc.roadAddressName())
                        .latitude(Double.parseDouble(doc.y()))
                        .longitude(Double.parseDouble(doc.x()))
                        .distance(doc.distance())
                        .build())
                .toList();

        return PlaceSearchResponseDTO.PlaceSearchResult.builder()
                .meta(PlaceSearchResponseDTO.Meta.builder()
                        .totalCount(meta.totalCount())
                        .isEnd(meta.isEnd())
                        .build())
                .places(places)
                .build();
    }

    private String truncate(String value) {
        if (value == null || value.length() <= 1000) return value;
        return value.substring(0, 1000);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record KakaoKeywordSearchResponse(
            KakaoMeta meta,
            List<KakaoDocument> documents
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record KakaoMeta(
            @JsonProperty("total_count") int totalCount,
            @JsonProperty("pageable_count") int pageableCount,
            @JsonProperty("is_end") boolean isEnd
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record KakaoDocument(
            String id,
            @JsonProperty("place_name") String placeName,
            @JsonProperty("category_name") String categoryName,
            @JsonProperty("category_group_code") String categoryGroupCode,
            @JsonProperty("category_group_name") String categoryGroupName,
            String phone,
            @JsonProperty("address_name") String addressName,
            @JsonProperty("road_address_name") String roadAddressName,
            String x,
            String y,
            @JsonProperty("place_url") String placeUrl,
            String distance
    ) {
    }
}
