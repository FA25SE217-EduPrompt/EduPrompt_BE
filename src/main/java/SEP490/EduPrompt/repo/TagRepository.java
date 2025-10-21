package SEP490.EduPrompt.repo;

import SEP490.EduPrompt.model.Tag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TagRepository extends JpaRepository<Tag, UUID> {
    Optional<Tag> findByTypeAndValue(String type, String value);

    List<Tag> findAllByTypeIn(List<String> types);
}