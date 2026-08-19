package gdg.sharinglog.domain.rotation;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Table(
        name = "chore_occurrences",
        indexes = {
                @Index(name = "idx_chore_occurrences_status_due", columnList = "status, due_at"),
                @Index(name = "idx_chore_occurrences_current_assignment", columnList = "current_assignment_id")
        },
        uniqueConstraints = @UniqueConstraint(
                name = "uk_chore_occurrences_chore_period",
                columnNames = {"chore_id", "schedule_revision_snapshot", "period_start"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Entity
public class ChoreOccurrence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false)
    private Long id;

    @Column(name = "public_id", nullable = false, updatable = false, unique = true, length = 36)
    private String publicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "chore_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_chore_occurrences_chore")
    )
    private Chore chore;

    @Column(name = "chore_name_snapshot", nullable = false, length = 100)
    private String choreNameSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "frequency_snapshot", nullable = false, length = 20)
    private ChoreFrequency frequencySnapshot;

    @Column(name = "time_zone_id_snapshot", nullable = false, length = 40)
    private String timeZoneIdSnapshot;

    @Column(name = "schedule_revision_snapshot", nullable = false)
    private long scheduleRevisionSnapshot;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end_exclusive", nullable = false)
    private LocalDate periodEndExclusive;

    @Column(name = "due_at", nullable = false)
    private Instant dueAt;

    @Column(name = "eligibility_snapshot_version", nullable = false)
    private int eligibilitySnapshotVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private OccurrenceStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "attention_reason", length = 60)
    private NoCandidateReason attentionReason;

    @Column(name = "attention_since")
    private Instant attentionSince;

    @Column(name = "last_decision_at")
    private Instant lastDecisionAt;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "current_assignment_id",
            unique = true,
            foreignKey = @ForeignKey(name = "fk_chore_occurrences_current_assignment")
    )
    private ChoreAssignmentAttempt currentAssignment;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "due_soon_24h_notified_at")
    private Instant dueSoon24hNotifiedAt;

    @Column(name = "due_soon_3h_notified_at")
    private Instant dueSoon3hNotifiedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    private ChoreOccurrence(
            Chore chore,
            LocalDate periodStart,
            LocalDate periodEndExclusive,
            Instant dueAt,
            Instant createdAt
    ) {
        this.chore = Objects.requireNonNull(chore, "업무는 필수입니다.");
        this.choreNameSnapshot = chore.getName();
        this.frequencySnapshot = chore.getFrequency();
        this.timeZoneIdSnapshot = chore.getGroup().getTimeZoneId();
        this.scheduleRevisionSnapshot = chore.getScheduleRevision();
        this.periodStart = Objects.requireNonNull(periodStart, "기간 시작일은 필수입니다.");
        this.periodEndExclusive = Objects.requireNonNull(periodEndExclusive, "기간 종료일은 필수입니다.");
        if (!periodEndExclusive.isAfter(periodStart)) {
            throw new IllegalArgumentException("기간 종료일은 시작일보다 뒤여야 합니다.");
        }
        this.dueAt = Objects.requireNonNull(dueAt, "마감 시각은 필수입니다.");
        validateScheduleSnapshot();
        this.createdAt = Objects.requireNonNull(createdAt, "생성 시각은 필수입니다.");
        this.publicId = UUID.randomUUID().toString();
        this.eligibilitySnapshotVersion = 1;
        this.status = OccurrenceStatus.NEEDS_ATTENTION;
    }

    private void validateScheduleSnapshot() {
        long periodDays = ChronoUnit.DAYS.between(periodStart, periodEndExclusive);
        long expectedDays = switch (frequencySnapshot) {
            case DAILY -> 1;
            case WEEKLY -> 7;
            case BIWEEKLY -> 14;
        };
        if (periodDays != expectedDays) {
            throw new IllegalArgumentException(
                    frequencySnapshot + " 회차 기간은 " + expectedDays + "일이어야 합니다."
            );
        }

        if (frequencySnapshot == ChoreFrequency.WEEKLY
                && periodStart.getDayOfWeek() != chore.getGroup().getWeekStartsOn()) {
            throw new IllegalArgumentException("주간 회차는 그룹의 주 시작 요일에 시작해야 합니다.");
        }
        if (frequencySnapshot == ChoreFrequency.BIWEEKLY) {
            long daysFromAnchor = ChronoUnit.DAYS.between(
                    chore.getBiweeklyAnchorDate(),
                    periodStart
            );
            if (Math.floorMod(daysFromAnchor, 14) != 0) {
                throw new IllegalArgumentException("격주 회차는 기준일의 14일 경계에 시작해야 합니다.");
            }
        }

        ZoneId zoneId = ZoneId.of(timeZoneIdSnapshot);
        LocalDate expectedDueDate = switch (frequencySnapshot) {
            case DAILY -> periodStart;
            case BIWEEKLY -> periodEndExclusive.minusDays(1);
            case WEEKLY -> periodStart.plusDays(Math.floorMod(
                    chore.getWeeklyDueDay().getValue()
                            - chore.getGroup().getWeekStartsOn().getValue(),
                    7
            ));
        };
        Instant expectedDueAt = expectedDueDate
                .atTime(chore.getDueTime())
                .atZone(zoneId)
                .toInstant();
        if (!dueAt.equals(expectedDueAt)) {
            throw new IllegalArgumentException("마감 시각이 업무 반복 규칙과 일치하지 않습니다.");
        }
    }

    public static ChoreOccurrence create(
            Chore chore,
            LocalDate periodStart,
            LocalDate periodEndExclusive,
            Instant dueAt,
            Instant createdAt
    ) {
        return new ChoreOccurrence(chore, periodStart, periodEndExclusive, dueAt, createdAt);
    }

    public Optional<GroupMember> currentAssignee() {
        return Optional.ofNullable(currentAssignment).map(ChoreAssignmentAttempt::getAssignee);
    }

    public void markDueSoon24hNotified(Instant notifiedAt) {
        this.dueSoon24hNotifiedAt = Objects.requireNonNull(notifiedAt, "알림 발송 시각은 필수입니다.");
    }

    public void markDueSoon3hNotified(Instant notifiedAt) {
        this.dueSoon3hNotifiedAt = Objects.requireNonNull(notifiedAt, "알림 발송 시각은 필수입니다.");
    }

    public void rescheduleToCurrentRevision(
            LocalDate periodStart,
            LocalDate periodEndExclusive,
            Instant dueAt
    ) {
        if (status.isTerminal()) {
            throw new IllegalStateException("종료된 회차의 일정 스냅샷은 변경할 수 없습니다.");
        }
        this.frequencySnapshot = chore.getFrequency();
        this.timeZoneIdSnapshot = chore.getGroup().getTimeZoneId();
        this.scheduleRevisionSnapshot = chore.getScheduleRevision();
        this.periodStart = Objects.requireNonNull(periodStart, "기간 시작일은 필수입니다.");
        this.periodEndExclusive = Objects.requireNonNull(
                periodEndExclusive,
                "기간 종료일은 필수입니다."
        );
        this.dueAt = Objects.requireNonNull(dueAt, "마감 시각은 필수입니다.");
        validateScheduleSnapshot();
    }

    public void assign(ChoreAssignmentAttempt assignment) {
        if (status != OccurrenceStatus.NEEDS_ATTENTION || currentAssignment != null) {
            throw new IllegalStateException("담당자가 없는 관리 필요 회차에만 배정할 수 있습니다.");
        }
        ChoreAssignmentAttempt required = Objects.requireNonNull(assignment, "배정 시도는 필수입니다.");
        if (required.getOccurrence() != this) {
            throw new IllegalArgumentException("배정 시도는 같은 회차에 속해야 합니다.");
        }
        if (!required.isActive()) {
            throw new IllegalArgumentException("이미 종료된 배정 시도는 현재 배정으로 지정할 수 없습니다.");
        }
        validateDecisionTime(required.getAssignedAt());
        this.currentAssignment = required;
        this.status = OccurrenceStatus.ASSIGNED;
        this.attentionReason = null;
        this.attentionSince = null;
        this.lastDecisionAt = required.getAssignedAt();
    }

    public void recordNoCandidate(NoCandidateReason reason, Instant decidedAt) {
        if (status != OccurrenceStatus.NEEDS_ATTENTION || currentAssignment != null) {
            throw new IllegalStateException("관리 필요 상태이고 현재 담당자가 없을 때만 후보 없음 결정을 기록할 수 있습니다.");
        }
        NoCandidateReason requiredReason =
                Objects.requireNonNull(reason, "후보 없음 사유는 필수입니다.");
        Instant effectiveDecidedAt =
                Objects.requireNonNull(decidedAt, "결정 시각은 필수입니다.");
        validateDecisionTime(effectiveDecidedAt);

        this.attentionReason = requiredReason;
        if (attentionSince == null) {
            this.attentionSince = effectiveDecidedAt;
        }
        this.lastDecisionAt = effectiveDecidedAt;
    }

    public void complete(Instant completedAt) {
        complete(completedAt, null);
    }

    public void complete(Instant completedAt, String actorNote) {
        Instant effectiveCompletedAt = Objects.requireNonNull(
                completedAt,
                "Completion time is required."
        );
        LocalDate completedOn = effectiveCompletedAt
                .atZone(ZoneId.of(timeZoneIdSnapshot))
                .toLocalDate();
        if (completedOn.isBefore(periodStart)) {
            throw new IllegalStateException("A future occurrence cannot be completed.");
        }
        close(
                OccurrenceStatus.COMPLETED,
                AssignmentEndReason.COMPLETED,
                effectiveCompletedAt,
                actorNote
        );
    }

    public void skipAlreadyDone(Instant skippedAt) {
        skipAlreadyDone(skippedAt, null);
    }

    public void skipAlreadyDone(Instant skippedAt, String actorNote) {
        close(
                OccurrenceStatus.SKIPPED,
                AssignmentEndReason.SKIPPED_ALREADY_DONE,
                skippedAt,
                actorNote
        );
    }

    public void cancelForPlanRegeneration(Instant cancelledAt) {
        if (status.isTerminal()) {
            throw new IllegalStateException("A terminal occurrence cannot be cancelled for regeneration.");
        }
        Instant effectiveCancelledAt = Objects.requireNonNull(
                cancelledAt,
                "Cancellation time is required."
        );
        if (status == OccurrenceStatus.ASSIGNED) {
            requireAssigned();
            currentAssignment.end(
                    AssignmentEndReason.PLAN_REGENERATED,
                    effectiveCancelledAt
            );
            currentAssignment = null;
        } else if (status != OccurrenceStatus.NEEDS_ATTENTION) {
            throw new IllegalStateException("Only an open occurrence can be regenerated.");
        }
        status = OccurrenceStatus.CANCELLED;
        closedAt = effectiveCancelledAt;
    }

    public void releaseForReassignment(AssignmentEndReason reason, Instant endedAt) {
        releaseForReassignment(reason, endedAt, null);
    }

    public void releaseForReassignment(
            AssignmentEndReason reason,
            Instant endedAt,
            String actorNote
    ) {
        if (!Objects.requireNonNull(reason, "배정 종료 사유는 필수입니다.").requiresReassignment()) {
            throw new IllegalArgumentException("재배정이 필요한 종료 사유만 사용할 수 있습니다.");
        }
        requireAssigned();
        currentAssignment.end(reason, endedAt, actorNote);
        currentAssignment = null;
        status = OccurrenceStatus.NEEDS_ATTENTION;
    }

    public void advanceEligibilitySnapshotVersion() {
        if (status.isTerminal()) {
            throw new IllegalStateException("종료된 회차의 가능 멤버 조건은 갱신할 수 없습니다.");
        }
        eligibilitySnapshotVersion++;
    }

    public void reopenCompleted(ChoreAssignmentAttempt assignment, Instant reopenedAt) {
        if (status != OccurrenceStatus.COMPLETED || currentAssignment != null) {
            throw new IllegalStateException("완료된 회차만 다시 열 수 있습니다.");
        }
        Instant effectiveReopenedAt =
                Objects.requireNonNull(reopenedAt, "완료 취소 시각은 필수입니다.");
        if (closedAt == null || effectiveReopenedAt.isBefore(closedAt)) {
            throw new IllegalArgumentException("완료 취소 시각은 완료 시각보다 빠를 수 없습니다.");
        }
        this.status = OccurrenceStatus.NEEDS_ATTENTION;
        this.closedAt = null;
        assign(assignment);
    }

    private void close(
            OccurrenceStatus terminalStatus,
            AssignmentEndReason reason,
            Instant endedAt,
            String actorNote
    ) {
        if (!terminalStatus.isTerminal()) {
            throw new IllegalArgumentException("종료 상태가 아닙니다.");
        }
        requireAssigned();
        Instant effectiveEndedAt = Objects.requireNonNull(endedAt, "종료 시각은 필수입니다.");
        currentAssignment.end(reason, effectiveEndedAt, actorNote);
        currentAssignment = null;
        status = terminalStatus;
        closedAt = effectiveEndedAt;
    }

    private void requireAssigned() {
        if (status != OccurrenceStatus.ASSIGNED || currentAssignment == null) {
            throw new IllegalStateException("현재 담당자가 있는 배정 상태에서만 처리할 수 있습니다.");
        }
    }

    private void validateDecisionTime(Instant decidedAt) {
        if (lastDecisionAt != null && decidedAt.isBefore(lastDecisionAt)) {
            throw new IllegalArgumentException("결정 시각은 이전 결정 시각보다 빠를 수 없습니다.");
        }
    }
}
