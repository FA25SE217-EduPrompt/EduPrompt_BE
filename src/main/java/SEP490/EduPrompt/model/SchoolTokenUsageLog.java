package SEP490.EduPrompt.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "school_token_usage_log")
public class SchoolTokenUsageLog {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "school_subscription_id")
    private SchoolSubscription schoolSubscription;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "tokens_used")
    private Integer tokensUsed;

    @Size(max = 50)
    @Column(name = "action_type", length = 50)
    private String actionType;

    @ColumnDefault("now()")
    @Column(name = "used_at")
    private Instant usedAt;

    @PrePersist
    public void onCreate()
    {
        this.usedAt = Instant.now();
    }
}