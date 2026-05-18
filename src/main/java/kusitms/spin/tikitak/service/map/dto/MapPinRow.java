package kusitms.spin.tikitak.service.map.dto;

import java.math.BigDecimal;

public interface MapPinRow {
	String getExternalPlaceId();
	String getName();
	BigDecimal getLatitude();
	BigDecimal getLongitude();
	String getAddress();
	Long getFeedCount();
	String getThumbnailUrl();
}
