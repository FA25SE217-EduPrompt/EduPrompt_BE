package SEP490.EduPrompt.repo;

import SEP490.EduPrompt.model.PromptVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PromptVersionRepository extends JpaRepository<PromptVersion, UUID> {
}