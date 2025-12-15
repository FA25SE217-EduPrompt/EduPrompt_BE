package SEP490.EduPrompt.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "teacher_token_usage_log")
public class TeacherTokenUsageLog {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "school_subscription_id")
    private UUID schoolSubscriptionId;

    @Column(name = "subscription_tier_id")
    private UUID subscriptionTierId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "user_id", insertable = false, updatable = false)
    private UUID userId;

    @Column(name = "tokens_used")
    private Integer tokensUsed;

    @ColumnDefault("now()")
    @Column(name = "used_at")
    private Instant usedAt;

    @PrePersist
    public void onCreate() {
        this.usedAt = Instant.now();
    }

}