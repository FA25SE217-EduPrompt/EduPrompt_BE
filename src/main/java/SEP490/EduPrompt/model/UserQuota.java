package SEP490.EduPrompt.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
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
@Table(name = "user_quota")
public class UserQuota {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @NotNull
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "user_id", insertable = false, updatable = false)
    private UUID userId;

    @Column(name = "testing_quota_remaining")
    private Integer testingQuotaRemaining;

    @Column(name = "testing_quota_limit")
    private Integer testingQuotaLimit;

    @Column(name = "optimization_quota_remaining")
    private Integer optimizationQuotaRemaining;

    @Column(name = "optimization_quota_limit")
    private Integer optimizationQuotaLimit;

    @Column(name = "quota_reset_date")
    private Instant quotaResetDate;

    @ColumnDefault("now()")
    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.SET_NULL)
    @JoinColumn(name = "school_subscription_id")
    private SchoolSubscription schoolSubscription;

    @Column(name = "school_subscription_id", insertable = false, updatable = false)
    private String schoolSubscriptionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.SET_NULL)
    @JoinColumn(name = "subscription_tier_id")
    private SubscriptionTier subscriptionTier;

    @Column(name = "subscription_tier_id", insertable = false, updatable = false)
    private String subscriptionTierId;

    @NotNull
    @ColumnDefault("0")
    @Column(name = "individual_token_remaining", nullable = false)
    private Integer individualTokenRemaining;

    @NotNull
    @ColumnDefault("0")
    @Column(name = "individual_token_limit", nullable = false)
    private Integer individualTokenLimit;

    @NotNull
    @ColumnDefault("0")
    @Column(name = "prompt_unlock_remaining", nullable = false)
    private Integer promptUnlockRemaining;

    @NotNull
    @ColumnDefault("0")
    @Column(name = "prompt_unlock_limit", nullable = false)
    private Integer promptUnlockLimit;

    @NotNull
    @ColumnDefault("0")
    @Column(name = "prompt_action_remaining", nullable = false)
    private Integer promptActionRemaining;

    @NotNull
    @ColumnDefault("0")
    @Column(name = "prompt_action_limit", nullable = false)
    private Integer promptActionLimit;

    @NotNull
    @ColumnDefault("0")
    @Column(name = "collection_action_remaining", nullable = false)
    private Integer collectionActionRemaining;

    @NotNull
    @ColumnDefault("0")
    @Column(name = "collection_action_limit", nullable = false)
    private Integer collectionActionLimit;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
        quotaResetDate = Instant.now().plusSeconds(2592000); //30 days
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

}