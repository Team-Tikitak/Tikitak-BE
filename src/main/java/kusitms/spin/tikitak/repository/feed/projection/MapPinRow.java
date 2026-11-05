package kusitms.spin.tikitak.repository.feed.projection;

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
