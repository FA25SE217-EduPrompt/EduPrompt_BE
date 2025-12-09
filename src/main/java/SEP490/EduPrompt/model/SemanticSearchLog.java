package SEP490.EduPrompt.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Builder
@AllArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "semantic_search_logs")
@NoArgsConstructor
public class SemanticSearchLog {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "user_id", insertable = false, updatable = false)
    private UUID userId;

    @Column(name = "query", length = Integer.MAX_VALUE)
    private String query;

    @Column(name = "filters")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> filters;

    @Column(name = "results_count")
    private Integer resultsCount;

    @Column(name = "execution_time_ms")
    private Integer executionTimeMs;

    @ColumnDefault("now()")
    @Column(name = "created_at")
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}