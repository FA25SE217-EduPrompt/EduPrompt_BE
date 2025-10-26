package SEP490.EduPrompt.repo;

import SEP490.EduPrompt.model.Attachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AttachmentRepository extends JpaRepository<Attachment, UUID> {
    /**
 * Finds attachments associated with the given prompt version ID.
 *
 * @param promptVersionId the UUID of the prompt version whose attachments should be retrieved
 * @return a list of Attachment entities linked to the specified prompt version, or an empty list if none exist
 */
List<Attachment> findByPromptVersionId(UUID promptVersionId);
}