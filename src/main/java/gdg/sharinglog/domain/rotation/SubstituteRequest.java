package gdg.sharinglog.domain.rotation;

import java.time.Instant;
import java.util.Objects;
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
        name = "substitute_requests",
        indexes = @Index(
                name = "idx_substitute_requests_occurrence_status",
                columnList = "occurrence_id, status"
        ),
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_substitute_requests_public_id",
                        columnNames = "public_id"
                ),
                @UniqueConstraint(
                        name = "uk_substitute_requests_occurrence_active",
                        columnNames = {"occurrence_id", "active_marker"}
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Entity
public class SubstituteRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false)
    private Long id;

    @Column(name = "public_id", nullable = false, updatable = false, length = 36)
    private String publicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "occurrence_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_substitute_requests_occurrence")
    )
    private ChoreOccurrence occurrence;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "requester_assignment_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_substitute_requests_requester_assignment")
    )
    private ChoreAssignmentAttempt requesterAssignment;

    @Column(name = "eligibility_snapshot_version", nullable = false)
    private int eligibilitySnapshotVersion;

    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SubstituteRequestStatus status;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "accepted_assignment_id",
            foreignKey = @ForeignKey(name = "fk_substitute_requests_accepted_assignment")
    )
    private ChoreAssignmentAttempt acceptedAssignment;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_response_at")
    private Instant lastResponseAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "active_marker")
    private Integer activeMarker;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    private SubstituteRequest(
            ChoreOccurrence occurrence,
            ChoreAssignmentAttempt requesterAssignment,
            String reason,
            Instant createdAt
    ) {
        this.occurrence = Objects.requireNonNull(occurrence, "회차는 필수입니다.");
        ChoreAssignmentAttempt requiredAssignment =
                Objects.requireNonNull(requesterAssignment, "요청 당시 배정은 필수입니다.");
        if (occurrence.getStatus() != OccurrenceStatus.ASSIGNED
                || occurrence.getCurrentAssignment() != requiredAssignment
                || !requiredAssignment.isActive()) {
            throw new IllegalArgumentException("현재 활성 배정에 대해서만 대타를 요청할 수 있습니다.");
        }
        this.requesterAssignment = requiredAssignment;
        this.eligibilitySnapshotVersion = occurrence.getEligibilitySnapshotVersion();
        this.reason = normalizeReason(reason);
        this.createdAt = Objects.requireNonNull(createdAt, "요청 시각은 필수입니다.");
        this.publicId = UUID.randomUUID().toString();
        this.status = SubstituteRequestStatus.PENDING;
        this.activeMarker = 1;
    }

    public static SubstituteRequest pending(
            ChoreOccurrence occurrence,
            ChoreAssignmentAttempt requesterAssignment,
            String reason,
            Instant createdAt
    ) {
        return new SubstituteRequest(occurrence, requesterAssignment, reason, createdAt);
    }

    public GroupMember requester() {
        return requesterAssignment.getAssignee();
    }

    public void recordResponse(Instant respondedAt) {
        requirePending();
        Instant effectiveRespondedAt =
                Objects.requireNonNull(respondedAt, "응답 시각은 필수입니다.");
        if (effectiveRespondedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("응답 시각은 요청 시각보다 빠를 수 없습니다.");
        }
        this.lastResponseAt = effectiveRespondedAt;
    }

    public void accept(
            ChoreAssignmentAttempt acceptedAssignment,
            Instant acceptedAt
    ) {
        requirePending();
        ChoreAssignmentAttempt requiredAssignment =
                Objects.requireNonNull(acceptedAssignment, "수락 배정은 필수입니다.");
        if (requiredAssignment.getOccurrence() != occurrence
                || !requiredAssignment.isActive()
                || requiredAssignment.getAssignee().getId()
                .equals(requester().getId())) {
            throw new IllegalArgumentException("대타 수락 배정이 요청과 일치하지 않습니다.");
        }
        Instant effectiveAcceptedAt =
                Objects.requireNonNull(acceptedAt, "수락 시각은 필수입니다.");
        recordResponse(effectiveAcceptedAt);
        this.acceptedAssignment = requiredAssignment;
        close(SubstituteRequestStatus.ACCEPTED, effectiveAcceptedAt);
    }

    public void exhaust(Instant exhaustedAt) {
        requirePending();
        close(
                SubstituteRequestStatus.EXHAUSTED,
                Objects.requireNonNull(exhaustedAt, "응답 종료 시각은 필수입니다.")
        );
    }

    public void cancel(Instant cancelledAt) {
        requirePending();
        close(
                SubstituteRequestStatus.CANCELLED,
                Objects.requireNonNull(cancelledAt, "취소 시각은 필수입니다.")
        );
    }

    private void close(SubstituteRequestStatus resolvedStatus, Instant at) {
        if (resolvedStatus.isPending()) {
            throw new IllegalArgumentException("종료 상태가 필요합니다.");
        }
        if (at.isBefore(createdAt)) {
            throw new IllegalArgumentException("종료 시각은 요청 시각보다 빠를 수 없습니다.");
        }
        this.status = resolvedStatus;
        this.resolvedAt = at;
        this.activeMarker = null;
    }

    private void requirePending() {
        if (!status.isPending() || activeMarker == null) {
            throw new IllegalStateException("진행 중인 대타 요청만 처리할 수 있습니다.");
        }
    }

    private static String normalizeReason(String reason) {
        String normalized = Objects.requireNonNull(reason, "대타 요청 사유는 필수입니다.").trim();
        if (normalized.isEmpty() || normalized.length() > 500) {
            throw new IllegalArgumentException("대타 요청 사유는 1~500자여야 합니다.");
        }
        return normalized;
    }
}
