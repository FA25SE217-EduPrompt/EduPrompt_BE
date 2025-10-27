package SEP490.EduPrompt.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
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
@Table(name = "optimization_queue")
public class OptimizationQueue {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "prompt_id", nullable = false)
    private Prompt prompt;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "requested_by", nullable = false)
    private User requestedBy;

    @Column(name = "requested_by", insertable = false, updatable = false)
    private UUID requestedById; //this is umm... hard to understand sometime..., it's userId in short

    @NotNull
    @Column(name = "input", nullable = false, length = Integer.MAX_VALUE)
    private String input;

    @Size(max = 50)
    @ColumnDefault("'pending'")
    @Column(name = "status", length = 50)
    private String status;

    @Size(max = 255)
    @ColumnDefault("'gpt-4o-mini'")
    @Column(name = "ai_model")
    private String aiModel;

    @Column(name = "output", length = Integer.MAX_VALUE)
    private String output;

    @Column(name = "error_message", length = Integer.MAX_VALUE)
    private String errorMessage;

    @ColumnDefault("now()")
    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Size(max = 255)
    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @ColumnDefault("0")
    @Column(name = "retry_count")
    private Integer retryCount;

    @NotNull
    @ColumnDefault("3")
    @Column(name = "max_retries", nullable = false)
    private Integer maxRetries;

    @ColumnDefault("0.7")
    @Column(name = "temperature")
    private Double temperature;

    @Column(name = "max_tokens")
    private Integer maxTokens;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

}