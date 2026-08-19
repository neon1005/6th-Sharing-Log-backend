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
        name = "chore_assignment_attempts",
        indexes = {
                @Index(name = "idx_assignment_attempts_assignee", columnList = "assignee_membership_id"),
                @Index(name = "idx_assignment_attempts_occurrence_end", columnList = "occurrence_id, ended_at")
        },
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_assignment_attempts_occurrence_sequence",
                        columnNames = {"occurrence_id", "sequence_number"}
                ),
                @UniqueConstraint(
                        name = "uk_assignment_attempts_one_active",
                        columnNames = {"occurrence_id", "active_marker"}
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Entity
public class ChoreAssignmentAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "occurrence_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_assignment_attempts_occurrence")
    )
    private ChoreOccurrence occurrence;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "assignee_membership_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_assignment_attempts_assignee")
    )
    private GroupMember assignee;

    @Column(name = "sequence_number", nullable = false)
    private int sequenceNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 40)
    private AssignmentTrigger trigger;

    @Column(name = "assigned_at", nullable = false, updatable = false)
    private Instant assignedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "end_reason", length = 40)
    private AssignmentEndReason endReason;

    @Column(name = "active_marker")
    private Integer activeMarker;

    @Column(name = "algorithm_version", nullable = false, length = 30)
    private String algorithmVersion;

    @Column(name = "decision_seed", nullable = false)
    private long decisionSeed;

    @Lob
    @Column(name = "candidate_snapshot", nullable = false, length = Length.LONG32)
    private String candidateSnapshot;

    @Column(name = "decision_summary", nullable = false, length = 500)
    private String decisionSummary;

    @Column(name = "actor_note", length = 500)
    private String actorNote;

    @Column(name = "completion_revoked_at")
    private Instant completionRevokedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "completion_revoked_by_membership_id",
            foreignKey = @ForeignKey(name = "fk_assignment_attempts_completion_revoked_by")
    )
    private GroupMember completionRevokedBy;

    @Column(name = "completion_revocation_note", length = 500)
    private String completionRevocationNote;

    private ChoreAssignmentAttempt(
            ChoreOccurrence occurrence,
            GroupMember assignee,
            int sequenceNumber,
            AssignmentTrigger trigger,
            Instant assignedAt,
            String algorithmVersion,
            long decisionSeed,
            String candidateSnapshot,
            String decisionSummary
    ) {
        this.occurrence = Objects.requireNonNull(occurrence, "회차는 필수입니다.");
        this.assignee = requireEligibleGroupMember(assignee, occurrence);
        if (sequenceNumber < 1) {
            throw new IllegalArgumentException("배정 순번은 1 이상이어야 합니다.");
        }
        this.sequenceNumber = sequenceNumber;
        this.trigger = Objects.requireNonNull(trigger, "배정 계기는 필수입니다.");
        this.assignedAt = Objects.requireNonNull(assignedAt, "배정 시각은 필수입니다.");
        this.algorithmVersion = requireText(algorithmVersion, "알고리즘 버전은 필수입니다.");
        this.decisionSeed = decisionSeed;
        this.candidateSnapshot = requireText(candidateSnapshot, "후보 스냅샷은 필수입니다.");
        this.decisionSummary = requireText(decisionSummary, "선택 사유는 필수입니다.");
        this.activeMarker = 1;
    }

    public static ChoreAssignmentAttempt assigned(
            ChoreOccurrence occurrence,
            GroupMember assignee,
            int sequenceNumber,
            AssignmentTrigger trigger,
            Instant assignedAt,
            String algorithmVersion,
            long decisionSeed,
            String candidateSnapshot,
            String decisionSummary
    ) {
        return new ChoreAssignmentAttempt(
                occurrence,
                assignee,
                sequenceNumber,
                trigger,
                assignedAt,
                algorithmVersion,
                decisionSeed,
                candidateSnapshot,
                decisionSummary
        );
    }

    public boolean isActive() {
        return endedAt == null && endReason == null && activeMarker != null;
    }

    void end(AssignmentEndReason endReason, Instant endedAt) {
        end(endReason, endedAt, null);
    }

    void end(AssignmentEndReason endReason, Instant endedAt, String actorNote) {
        if (!isActive()) {
            throw new IllegalStateException("이미 종료된 배정 시도입니다.");
        }
        Instant effectiveEndedAt = Objects.requireNonNull(endedAt, "배정 종료 시각은 필수입니다.");
        if (effectiveEndedAt.isBefore(assignedAt)) {
            throw new IllegalArgumentException("배정 종료 시각은 배정 시각보다 빠를 수 없습니다.");
        }
        AssignmentEndReason requiredEndReason =
                Objects.requireNonNull(endReason, "배정 종료 사유는 필수입니다.");
        String normalizedActorNote = normalizeOptionalNote(actorNote);
        this.endReason = requiredEndReason;
        this.endedAt = effectiveEndedAt;
        this.activeMarker = null;
        this.actorNote = normalizedActorNote;
    }

    public void revokeCompletion(
            GroupMember revokedBy,
            Instant revokedAt,
            String revocationNote
    ) {
        if (endReason != AssignmentEndReason.COMPLETED || endedAt == null || isActive()) {
            throw new IllegalStateException("완료로 종료된 배정만 완료 취소할 수 있습니다.");
        }
        if (completionRevokedAt != null) {
            throw new IllegalStateException("이미 완료 취소된 배정입니다.");
        }
        GroupMember requiredRevokedBy =
                Objects.requireNonNull(revokedBy, "완료 취소 멤버는 필수입니다.");
        if (requiredRevokedBy.getGroup() != occurrence.getChore().getGroup()) {
            throw new IllegalArgumentException("완료 취소 멤버는 회차와 같은 그룹에 속해야 합니다.");
        }
        Instant effectiveRevokedAt =
                Objects.requireNonNull(revokedAt, "완료 취소 시각은 필수입니다.");
        if (effectiveRevokedAt.isBefore(endedAt)) {
            throw new IllegalArgumentException("완료 취소 시각은 완료 시각보다 빠를 수 없습니다.");
        }
        this.completionRevokedBy = requiredRevokedBy;
        this.completionRevokedAt = effectiveRevokedAt;
        this.completionRevocationNote = normalizeOptionalNote(revocationNote);
    }

    public boolean isEffectiveCompletion() {
        return endReason == AssignmentEndReason.COMPLETED && completionRevokedAt == null;
    }

    private static GroupMember requireEligibleGroupMember(
            GroupMember assignee,
            ChoreOccurrence occurrence
    ) {
        GroupMember required = Objects.requireNonNull(assignee, "담당 멤버는 필수입니다.");
        if (required.getGroup() != occurrence.getChore().getGroup()) {
            throw new IllegalArgumentException("담당 멤버는 회차와 같은 그룹에 속해야 합니다.");
        }
        if (!required.isActive()) {
            throw new IllegalArgumentException("탈퇴 멤버에게 업무를 배정할 수 없습니다.");
        }
        return required;
    }

    private static String requireText(String value, String message) {
        String required = Objects.requireNonNull(value, message).trim();
        if (required.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return required;
    }

    private static String normalizeOptionalNote(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() > 500) {
            throw new IllegalArgumentException("처리 메모는 500자 이하여야 합니다.");
        }
        return normalized;
    }
}
