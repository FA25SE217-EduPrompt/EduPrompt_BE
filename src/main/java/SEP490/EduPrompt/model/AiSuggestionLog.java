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
@Table(name = "ai_suggestion_logs")
public class AiSuggestionLog {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "prompt_id", nullable = false)
    private Prompt prompt;

    @Column(name = "requested_by", nullable = false)
    private UUID requestedBy;

    @Column(name = "input", nullable = false, length = Integer.MAX_VALUE)
    private String input;

    @Column(name = "output", length = Integer.MAX_VALUE)
    private String output;

    @Column(name = "ai_model")
    private String aiModel;

    @ColumnDefault("'pending'")
    @Column(name = "status", nullable = false, length = 50)
    private String status;

    @ColumnDefault("now()")
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.SET_NULL)
    @JoinColumn(name = "optimization_queue_id")
    private OptimizationQueue optimizationQueue;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

}