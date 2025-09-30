package SEP490.EduPrompt;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EnableJpaRepositories(basePackages = "SEP490.EduPrompt.repo")
@EntityScan(basePackages = "SEP490.EduPrompt.model")
public class EduPromptApplication {

	public static void main(String[] args) {
		SpringApplication.run(EduPromptApplication.class, args);
	}

}
