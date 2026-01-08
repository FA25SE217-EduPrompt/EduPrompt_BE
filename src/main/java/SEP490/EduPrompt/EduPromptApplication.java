package SEP490.EduPrompt;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableJpaRepositories(basePackages = "SEP490.EduPrompt.repo")
@EntityScan(basePackages = "SEP490.EduPrompt.model")
@EnableCaching
@EnableScheduling
@EnableAsync
public class EduPromptApplication {

    public static void main(String[] args) {
        SpringApplication.run(EduPromptApplication.class, args);
    }

}
