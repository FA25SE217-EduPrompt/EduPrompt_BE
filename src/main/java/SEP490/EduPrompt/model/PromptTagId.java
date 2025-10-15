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
public class PromptTagId implements Serializable {
    @Serial
    private static final long serialVersionUID = -7017438153811612078L;
    @NotNull
    @Column(name = "prompt_id", nullable = false)
    private UUID promptId;

    @NotNull
    @Column(name = "tag_id", nullable = false)
    private UUID tagId;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        PromptTagId entity = (PromptTagId) o;
        return Objects.equals(this.tagId, entity.tagId) &&
                Objects.equals(this.promptId, entity.promptId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tagId, promptId);
    }

}