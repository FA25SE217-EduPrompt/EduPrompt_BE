package SEP490.EduPrompt.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "prompt_scores")
public class PromptScore {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "prompt_id", nullable = false)
    private Prompt prompt;

    @Column(name = "prompt_id", insertable = false, updatable = false)
    private UUID promptId;

    @ManyToOne(fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "version_id")
    private PromptVersion version;

    @Column(name = "version_id", insertable = false, updatable = false)
    private UUID versionId;

    @NotNull
    @Column(name = "overall_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal overallScore;

    @Column(name = "instruction_clarity_score", precision = 5, scale = 2)
    private BigDecimal instructionClarityScore;

    @Column(name = "context_completeness_score", precision = 5, scale = 2)
    private BigDecimal contextCompletenessScore;

    @Column(name = "output_specification_score", precision = 5, scale = 2)
    private BigDecimal outputSpecificationScore;

    @Column(name = "constraint_strength_score", precision = 5, scale = 2)
    private BigDecimal constraintStrengthScore;

    @Column(name = "curriculum_alignment_score", precision = 5, scale = 2)
    private BigDecimal curriculumAlignmentScore;

    @Column(name = "pedagogical_quality_score", precision = 5, scale = 2)
    private BigDecimal pedagogicalQualityScore;

    @Column(name = "detected_weaknesses")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> detectedWeaknesses;

    @Column(name = "detected_context")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> detectedContext;

    @ColumnDefault("now()")
    @Column(name = "created_at")
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

}