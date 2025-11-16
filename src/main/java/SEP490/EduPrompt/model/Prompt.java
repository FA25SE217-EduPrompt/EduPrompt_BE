package SEP490.EduPrompt.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
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
@Table(name = "prompts")
public class Prompt {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.SET_NULL)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.SET_NULL)
    @JoinColumn(name = "collection_id")
    private Collection collection;

    @Column(name = "title", length = Integer.MAX_VALUE)
    private String title;

    @Column(name = "description", length = Integer.MAX_VALUE)
    private String description;

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

    @ColumnDefault("'private'")
    @Column(name = "visibility", nullable = false, length = 50)
    private String visibility;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @ColumnDefault("now()")
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @ColumnDefault("now()")
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @ColumnDefault("false")
    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.SET_NULL)
    @JoinColumn(name = "current_version_id")
    private PromptVersion currentVersion;

    @Size(max = 255)
    @Column(name = "gemini_file_id")
    private String geminiFileId;

    @Column(name = "last_indexed_at")
    private Instant lastIndexedAt;

    @Size(max = 50)
    @ColumnDefault("'pending'")
    @Column(name = "indexing_status", length = 50)
    private String indexingStatus;

}