package gdg.sharinglog.domain.rotation;

import java.time.Instant;
import java.util.Objects;

import gdg.sharinglog.domain.GroupMember;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.Length;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Table(
        name = "rotation_decision_logs",
        indexes = @Index(
                name = "idx_rotation_decision_logs_occurrence",
                columnList = "occurrence_id"
        ),
        uniqueConstraints = @UniqueConstraint(
                name = "uk_rotation_decision_logs_occurrence_sequence",
                columnNames = {"occurrence_id", "decision_sequence"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Entity
public class RotationDecisionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "occurrence_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_rotation_decision_logs_occurrence")
    )
    private ChoreOccurrence occurrence;

    @Column(name = "decision_sequence", nullable = false)
    private int decisionSequence;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 40)
    private AssignmentTrigger trigger;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, length = 30)
    private RotationDecisionOutcome outcome;

    @Enumerated(EnumType.STRING)
    @Column(name = "no_candidate_reason", length = 60)
    private NoCandidateReason noCandidateReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "selected_membership_id",
            foreignKey = @ForeignKey(name = "fk_rotation_decision_logs_selected_member")
    )
    private GroupMember selectedMember;

    @Column(name = "algorithm_version", nullable = false, length = 30)
    private String algorithmVersion;

    @Column(name = "decision_seed", nullable = false)
    private long decisionSeed;

    @Lob
    @Column(name = "candidate_snapshot", nullable = false, length = Length.LONG32)
    private String candidateSnapshot;

    @Column(name = "decision_summary", nullable = false, length = 500)
    private String decisionSummary;

    @Column(name = "decided_at", nullable = false, updatable = false)
    private Instant decidedAt;

    private RotationDecisionLog(
            ChoreOccurrence occurrence,
            int decisionSequence,
            AssignmentTrigger trigger,
            RotationDecisionOutcome outcome,
            NoCandidateReason noCandidateReason,
            GroupMember selectedMember,
            String algorithmVersion,
            long decisionSeed,
            String candidateSnapshot,
            String decisionSummary,
            Instant decidedAt
    ) {
        this.occurrence = Objects.requireNonNull(occurrence, "회차는 필수입니다.");
        if (decisionSequence < 1) {
            throw new IllegalArgumentException("결정 순번은 1 이상이어야 합니다.");
        }
        this.decisionSequence = decisionSequence;
        this.trigger = Objects.requireNonNull(trigger, "배정 계기는 필수입니다.");
        this.outcome = Objects.requireNonNull(outcome, "결정 결과는 필수입니다.");
        this.noCandidateReason = noCandidateReason;
        this.selectedMember = selectedMember;
        this.algorithmVersion = requireText(algorithmVersion, "알고리즘 버전은 필수입니다.");
        this.decisionSeed = decisionSeed;
        this.candidateSnapshot = requireText(candidateSnapshot, "후보 스냅샷은 필수입니다.");
        this.decisionSummary = requireText(decisionSummary, "결정 사유는 필수입니다.");
        this.decidedAt = Objects.requireNonNull(decidedAt, "결정 시각은 필수입니다.");
        validateOutcome();
    }

    public static RotationDecisionLog assigned(
            ChoreOccurrence occurrence,
            int decisionSequence,
            AssignmentTrigger trigger,
            GroupMember selectedMember,
            String algorithmVersion,
            long decisionSeed,
            String candidateSnapshot,
            String decisionSummary,
            Instant decidedAt
    ) {
        return new RotationDecisionLog(
                occurrence,
                decisionSequence,
                trigger,
                RotationDecisionOutcome.ASSIGNED,
                null,
                Objects.requireNonNull(selectedMember, "선택 멤버는 필수입니다."),
                algorithmVersion,
                decisionSeed,
                candidateSnapshot,
                decisionSummary,
                decidedAt
        );
    }

    public static RotationDecisionLog noCandidate(
            ChoreOccurrence occurrence,
            int decisionSequence,
            AssignmentTrigger trigger,
            NoCandidateReason noCandidateReason,
            String algorithmVersion,
            long decisionSeed,
            String candidateSnapshot,
            String decisionSummary,
            Instant decidedAt
    ) {
        return new RotationDecisionLog(
                occurrence,
                decisionSequence,
                trigger,
                RotationDecisionOutcome.NO_CANDIDATE,
                Objects.requireNonNull(noCandidateReason, "후보 없음 사유는 필수입니다."),
                null,
                algorithmVersion,
                decisionSeed,
                candidateSnapshot,
                decisionSummary,
                decidedAt
        );
    }

    public static RotationDecisionLog noCandidate(
            ChoreOccurrence occurrence,
            int decisionSequence,
            AssignmentTrigger trigger,
            String algorithmVersion,
            long decisionSeed,
            String candidateSnapshot,
            String decisionSummary,
            Instant decidedAt
    ) {
        return noCandidate(
                occurrence,
                decisionSequence,
                trigger,
                NoCandidateReason.NO_ACTIVE_ELIGIBLE_NON_DECLINED_CANDIDATE,
                algorithmVersion,
                decisionSeed,
                candidateSnapshot,
                decisionSummary,
                decidedAt
        );
    }

    private void validateOutcome() {
        if (outcome == RotationDecisionOutcome.ASSIGNED && selectedMember == null) {
            throw new IllegalArgumentException("배정 성공 결정에는 선택 멤버가 필요합니다.");
        }
        if (outcome == RotationDecisionOutcome.ASSIGNED && noCandidateReason != null) {
            throw new IllegalArgumentException("배정 성공 결정에는 후보 없음 사유가 없어야 합니다.");
        }
        if (outcome == RotationDecisionOutcome.NO_CANDIDATE && selectedMember != null) {
            throw new IllegalArgumentException("후보 없음 결정에는 선택 멤버가 없어야 합니다.");
        }
        if (outcome == RotationDecisionOutcome.NO_CANDIDATE && noCandidateReason == null) {
            throw new IllegalArgumentException("후보 없음 결정에는 사유가 필요합니다.");
        }
        if (selectedMember != null
                && selectedMember.getGroup() != occurrence.getChore().getGroup()) {
            throw new IllegalArgumentException("선택 멤버는 회차와 같은 그룹에 속해야 합니다.");
        }
    }

    private static String requireText(String value, String message) {
        String required = Objects.requireNonNull(value, message).trim();
        if (required.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return required;
    }
}
