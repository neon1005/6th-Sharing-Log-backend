package gdg.sharinglog.domain.rotation;

import java.time.Instant;
import java.util.Objects;

import gdg.sharinglog.domain.GroupMember;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Table(
        name = "occurrence_eligible_members",
        indexes = @Index(name = "idx_occurrence_eligible_members_member", columnList = "membership_id"),
        uniqueConstraints = @UniqueConstraint(
                name = "uk_occurrence_eligible_members_snapshot_member",
                columnNames = {"occurrence_id", "snapshot_version", "membership_id"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Entity
public class OccurrenceEligibleMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "occurrence_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_occurrence_eligible_members_occurrence")
    )
    private ChoreOccurrence occurrence;

    @Column(name = "snapshot_version", nullable = false)
    private int snapshotVersion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "membership_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_occurrence_eligible_members_membership")
    )
    private GroupMember member;

    @Column(name = "member_activation_generation", nullable = false)
    private long memberActivationGeneration;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public OccurrenceEligibleMember(
            ChoreOccurrence occurrence,
            int snapshotVersion,
            GroupMember member,
            Instant createdAt
    ) {
        this.occurrence = Objects.requireNonNull(occurrence, "회차는 필수입니다.");
        if (snapshotVersion < 1) {
            throw new IllegalArgumentException("스냅샷 버전은 1 이상이어야 합니다.");
        }
        this.snapshotVersion = snapshotVersion;
        this.member = Objects.requireNonNull(member, "가능 멤버는 필수입니다.");
        if (member.getGroup() != occurrence.getChore().getGroup()) {
            throw new IllegalArgumentException("가능 멤버는 회차와 같은 그룹에 속해야 합니다.");
        }
        this.memberActivationGeneration = member.getActivationGeneration();
        if (memberActivationGeneration < 1) {
            throw new IllegalArgumentException("멤버 활성 세대는 1 이상이어야 합니다.");
        }
        this.createdAt = Objects.requireNonNull(createdAt, "스냅샷 생성 시각은 필수입니다.");
    }

    public boolean belongsToCurrentActivation() {
        return memberActivationGeneration == member.getActivationGeneration();
    }
}
