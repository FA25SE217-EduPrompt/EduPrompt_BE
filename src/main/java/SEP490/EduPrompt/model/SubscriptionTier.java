package SEP490.EduPrompt.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;

import java.time.Instant;
import java.util.UUID;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "subscription_tiers")
public class SubscriptionTier {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @Size(max = 255)
    @NotNull
    @Column(name = "name", nullable = false)
    private String name;

    @NotNull
    @ColumnDefault("0")
    @Column(name = "price", nullable = false)
    private Double price;

    @NotNull
    @ColumnDefault("0")
    @Column(name = "individual_token_limit", nullable = false)
    private Integer individualTokenLimit;

    @NotNull
    @ColumnDefault("0")
    @Column(name = "testing_quota_limit", nullable = false)
    private Integer testingQuotaLimit;

    @NotNull
    @ColumnDefault("0")
    @Column(name = "optimization_quota_limit", nullable = false)
    private Integer optimizationQuotaLimit;

    @Column(name = "description", length = Integer.MAX_VALUE)
    private String description;

    @NotNull
    @ColumnDefault("true")
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = false;

    @NotNull
    @ColumnDefault("now()")
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @NotNull
    @ColumnDefault("now()")
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @NotNull
    @ColumnDefault("0")
    @Column(name = "prompt_unlock_limit", nullable = false)
    private Integer promptUnlockLimit;

    @NotNull
    @ColumnDefault("0")
    @Column(name = "prompt_action_limit", nullable = false)
    private Integer promptActionLimit;

    @NotNull
    @ColumnDefault("0")
    @Column(name = "collection_action_limit", nullable = false)
    private Integer collectionActionLimit;

    @ColumnDefault("0")
    @Column(name = "points_limit")
    private Long pointsLimit;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}