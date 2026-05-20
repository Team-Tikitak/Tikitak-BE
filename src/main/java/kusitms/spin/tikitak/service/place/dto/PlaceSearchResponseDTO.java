package kusitms.spin.tikitak.service.place.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

public class PlaceSearchResponseDTO {

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PlaceSearchResult {
        private Meta meta;
        private List<Place> places;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Meta {
        private int totalCount;
        private boolean isEnd;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Place {
        private String kakaoPlaceId;
        private String name;
        private String category;
        private String address;
        private String roadAddress;
        private double latitude;
        private double longitude;
        private String distance;
    }
}
