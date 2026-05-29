package kusitms.spin.tikitak.service.home;

import kusitms.spin.tikitak.service.home.dto.HomeResponseDTO;

import java.util.List;

class MayRecommendedPlaces {

	private static final String RECOMMEND_PLACE_PATH = "recommend_place/";

	static List<HomeResponseDTO.RecommendedPlaceItemDTO> all(String publicBaseUrl) {
		String base = normalizeBaseUrl(publicBaseUrl) + "/" + RECOMMEND_PLACE_PATH;
		return List.of(
		item("앤트러사이트 서교점",
			"폐공장 개조 감성 카페. 5월 정원 테라스에서 모각작하기 좋아요.",
			base + "%EC%97%94%ED%8A%B8%EB%9F%AC%EC%82%AC%EC%9D%B4%ED%8A%B8%20%EC%84%9C%EA%B5%90%EC%A0%90.jpg",
			"https://map.kakao.com/link/search/앤트러사이트서교"),
		item("망원한강공원",
			"돗자리 대여 가능한 봄 피크닉 명소. 망원시장 먹거리랑 조합하면 완벽해요.",
			base + "%EB%A7%9D%EC%9B%90%ED%95%9C%EA%B0%95%EA%B3%B5%EC%9B%90.jpg",
			"https://map.kakao.com/link/search/망원한강공원"),
		item("훌리건 커피",
			"지하 벙커 좌석이 있는 브런치 카페. 에그인헬·스테이크 봄바가 시그니처예요.",
			base + "%ED%9B%8C%EB%A6%AC%EA%B1%B4%20%EC%BB%A4%ED%94%BC.jpg",
			"https://map.kakao.com/link/search/훌리건커피"),
		item("KT&G 상상마당 홍대",
			"갤러리·디자인샵·공연장·영화관이 한 건물에. 비 오는 날 팀 나들이로 좋아요.",
			base + "KT%26G%20%EC%83%81%EC%83%81%EB%A7%88%EB%8B%B9%20%ED%99%8D%EB%8C%80.jpg",
			"https://map.kakao.com/link/search/KT&G상상마당홍대"),
		item("연남살롱",
			"연남동 골목 레트로 감성 공간. 산책 후 팀 대화 타임으로 어울려요.",
			base + "%EC%97%B0%EB%82%A8%EC%82%B4%EB%A1%B1.jpg",
			"https://map.kakao.com/link/search/연남살롱"),
		item("망원시장",
			"홍대 근처 전통시장. 피크닉 먹거리 챙기기 좋아요.",
			base + "%EB%A7%9D%EC%9B%90%EC%8B%9C%EC%9E%A5.jpg",
			"https://map.kakao.com/link/search/망원시장"),
		item("소바하우스 멘야준",
			"깊은 감칠맛을 느끼고 싶다면 추천. 쇼유라멘이 맛있는 집이에요.",
			base + "%EC%86%8C%EB%B0%94%ED%95%98%EC%9A%B0%EC%8A%A4%20%EB%A9%98%EC%95%BC%EC%A4%80.jpg",
			"https://map.kakao.com/link/search/소바하우스멘야준"),
		item("하쿠텐",
			"홍대 정통 라멘 맛집. 진한 육수 한 그릇이 생각날 때 가기 좋아요.",
			base + "%ED%95%98%EC%BF%A0%ED%85%90.jpg",
			"https://map.kakao.com/link/search/하쿠텐")
		);
	}

	private static String normalizeBaseUrl(String publicBaseUrl) {
		if (publicBaseUrl == null || publicBaseUrl.isBlank()) {
			return "";
		}
		return publicBaseUrl.replaceAll("/+$", "");
	}

	private static HomeResponseDTO.RecommendedPlaceItemDTO item(
		String name, String curation, String imageUrl, String kakaoMapUrl
	) {
		return HomeResponseDTO.RecommendedPlaceItemDTO.builder()
			.name(name)
			.curation(curation)
			.imageUrl(imageUrl)
			.kakaoMapUrl(kakaoMapUrl)
			.build();
	}
}
