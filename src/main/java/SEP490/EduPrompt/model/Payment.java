package SEP490.EduPrompt.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "payments")
public class Payment {
    @Id
    @Column(name = "id", nullable = false)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private UUID id;

    @Size(max = 255)
    @NotNull
    @Column(name = "txn_ref", nullable = false)
    private String txnRef;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.RESTRICT)
    @JoinColumn(name = "tier_id", nullable = false)
    private SubscriptionTier tier;

    @NotNull
    @Column(name = "amount", nullable = false)
    private Long amount;

    @Size(max = 255)
    @Column(name = "order_info")
    private String orderInfo;

    @Size(max = 20)
    @NotNull
    @ColumnDefault("'PENDING'")
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Size(max = 50)
    @Column(name = "vnp_transaction_no", length = 50)
    private String vnpTransactionNo;

    @Size(max = 10)
    @Column(name = "vnp_response_code", length = 10)
    private String vnpResponseCode;

    @Column(name = "vnp_secure_hash", length = Integer.MAX_VALUE)
    private String vnpSecureHash;

    @NotNull
    @ColumnDefault("now()")
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @PrePersist
    public void onCreate()
    {
        this.createdAt = Instant.now();
        this.paidAt = Instant.now();
    }
}