package kusitms.spin.tikitak.service.auth;

public interface OAuthStateStore {

	void save(String state, String provider);

	boolean consume(String state, String provider);
}
