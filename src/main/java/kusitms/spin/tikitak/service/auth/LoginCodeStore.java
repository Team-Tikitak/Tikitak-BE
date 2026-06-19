package kusitms.spin.tikitak.service.auth;

import kusitms.spin.tikitak.service.auth.dto.LoginCodePayload;

import java.util.Optional;

public interface LoginCodeStore {

	void save(String code, LoginCodePayload payload);

	Optional<LoginCodePayload> consume(String code);
}
