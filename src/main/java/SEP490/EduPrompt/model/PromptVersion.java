package SEP490.EduPrompt.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;
import java.util.UUID;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "prompt_versions")
public class PromptVersion {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "prompt_id", nullable = false)
    private Prompt prompt;

    @Column(name = "prompt_id", insertable = false, updatable = false)
    private UUID promptId;

    @Column(name = "instruction", length = Integer.MAX_VALUE)
    private String instruction;

    @Column(name = "context", length = Integer.MAX_VALUE)
    private String context;

    @Column(name = "input_example", length = Integer.MAX_VALUE)
    private String inputExample;

    @Column(name = "output_format", length = Integer.MAX_VALUE)
    private String outputFormat;

    @Column(name = "constraints", length = Integer.MAX_VALUE)
    private String constraints;

    @Column(name = "editor_id", nullable = false)
    private UUID editorId;

    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    @ColumnDefault("false")
    @Column(name = "is_ai_generated", nullable = false)
    private Boolean isAiGenerated;

    @ColumnDefault("now()")
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}