package kusitms.spin.tikitak;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@EnableJpaAuditing
@SpringBootApplication
public class TikitakApplication {

	public static void main(String[] args) {
		SpringApplication.run(TikitakApplication.class, args);
	}

}
