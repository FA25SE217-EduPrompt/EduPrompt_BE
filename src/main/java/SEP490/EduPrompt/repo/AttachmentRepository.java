package SEP490.EduPrompt.repo;

import SEP490.EduPrompt.model.Attachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AttachmentRepository extends JpaRepository<Attachment, UUID> {
    List<Attachment> findByPromptVersionId(UUID promptVersionId);
}