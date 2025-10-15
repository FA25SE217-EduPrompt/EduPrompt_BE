package SEP490.EduPrompt.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.Hibernate;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Embeddable
public class CollectionTagId implements Serializable {
    @Serial
    private static final long serialVersionUID = -1986435153979630181L;
    @NotNull
    @Column(name = "collection_id", nullable = false)
    private UUID collectionId;

    @NotNull
    @Column(name = "tag_id", nullable = false)
    private UUID tagId;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        CollectionTagId entity = (CollectionTagId) o;
        return Objects.equals(this.tagId, entity.tagId) &&
                Objects.equals(this.collectionId, entity.collectionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tagId, collectionId);
    }

}