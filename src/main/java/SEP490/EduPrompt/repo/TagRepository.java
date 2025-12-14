package SEP490.EduPrompt.repo;

import SEP490.EduPrompt.model.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TagRepository extends JpaRepository<Tag, UUID> {
    Optional<Tag> findByTypeAndValue(String type, String value);

    List<Tag> findAllByTypeIn(List<String> types);

    List<Tag> findAllByValueIn(List<String> values);

    @Query("SELECT t FROM Tag t WHERE t.id IN :ids")
    List<Tag> findAllByIdIn(@Param("ids") List<UUID> ids);

    Page<Tag> findByType(String type, Pageable pageable);

    @Query("SELECT DISTINCT t.type FROM Tag t")
    List<String> findDistinctTypes();

    @Query("SELECT t FROM Tag t WHERE LOWER(t.type) IN :types")
    Page<Tag> findByTypeInIgnoreCase(@Param("types") List<String> types, Pageable pageable);

}