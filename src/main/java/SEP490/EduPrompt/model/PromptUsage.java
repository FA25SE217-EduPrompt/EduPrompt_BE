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
@Table(name = "prompt_usages")
public class PromptUsage {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "prompt_id", nullable = false)
    private Prompt prompt;

    @Column(name = "prompt_id", updatable = false)
    private UUID promptId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "user_id", updatable = false)
    private UUID userId;

    @ColumnDefault("now()")
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Size(max = 255)
    @ColumnDefault("'gpt-4o-mini'")
    @Column(name = "ai_model")
    private String aiModel;

    @Column(name = "output", length = Integer.MAX_VALUE)
    private String output;

    @Column(name = "execution_time_ms")
    private Integer executionTimeMs;

    @Size(max = 255)
    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @Column(name = "input_text", length = Integer.MAX_VALUE)
    private String inputText;

    @Column(name = "tokens_used")
    private Integer tokensUsed;

    @ColumnDefault("0.7")
    @Column(name = "temperature")
    private Double temperature;

    @Column(name = "max_tokens")
    private Integer maxTokens;

    @ColumnDefault("1.0")
    @Column(name = "top_p")
    private Double topP;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}