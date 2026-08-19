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
        name = "chore_eligible_members",
        indexes = @Index(name = "idx_chore_eligible_members_member", columnList = "membership_id"),
        uniqueConstraints = @UniqueConstraint(
                name = "uk_chore_eligible_members_chore_member",
                columnNames = {"chore_id", "membership_id"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Entity
public class ChoreEligibleMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "chore_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_chore_eligible_members_chore")
    )
    private Chore chore;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "membership_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_chore_eligible_members_membership")
    )
    private GroupMember member;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "member_activation_generation", nullable = false)
    private long memberActivationGeneration;

    @Column(name = "enrolled_at", nullable = false)
    private Instant enrolledAt;

    @Column(name = "disabled_at")
    private Instant disabledAt;

    @Column(name = "fairness_credit", nullable = false)
    private long fairnessCredit;

    public ChoreEligibleMember(Chore chore, GroupMember member) {
        this(chore, member, chore.getCreatedAt(), 0L);
    }

    public ChoreEligibleMember(
            Chore chore,
            GroupMember member,
            Instant enrolledAt,
            long fairnessCredit
    ) {
        this.chore = Objects.requireNonNull(chore, "업무는 필수입니다.");
        this.member = Objects.requireNonNull(member, "가능 멤버는 필수입니다.");
        if (member.getGroup() != chore.getGroup()) {
            throw new IllegalArgumentException("가능 멤버는 업무와 같은 그룹에 속해야 합니다.");
        }
        requireActive(member);
        this.enabled = true;
        this.memberActivationGeneration = requireActivationGeneration(member);
        this.enrolledAt = Objects.requireNonNull(enrolledAt, "로테이션 등록 시각은 필수입니다.");
        this.fairnessCredit = requireFairnessCredit(fairnessCredit);
    }

    public void enableAtBack(Instant enrolledAt, long fairnessCredit) {
        requireActive(member);
        this.enabled = true;
        this.memberActivationGeneration = requireActivationGeneration(member);
        this.enrolledAt = Objects.requireNonNull(enrolledAt, "로테이션 등록 시각은 필수입니다.");
        this.disabledAt = null;
        this.fairnessCredit = requireFairnessCredit(fairnessCredit);
    }

    public void disable(Instant disabledAt) {
        if (!enabled) {
            return;
        }
        this.enabled = false;
        this.disabledAt = Objects.requireNonNull(disabledAt, "로테이션 제외 시각은 필수입니다.");
    }

    public boolean belongsToCurrentActivation() {
        return memberActivationGeneration == member.getActivationGeneration();
    }

    public long effectiveAssignmentCount(long actualAssignmentCount) {
        if (actualAssignmentCount < 0) {
            throw new IllegalArgumentException("실제 유효 할당 횟수는 음수일 수 없습니다.");
        }
        return Math.addExact(actualAssignmentCount, fairnessCredit);
    }

    private static void requireActive(GroupMember member) {
        if (!member.isActive()) {
            throw new IllegalArgumentException("활성 멤버만 로테이션에 등록할 수 있습니다.");
        }
    }

    private static long requireActivationGeneration(GroupMember member) {
        long generation = member.getActivationGeneration();
        if (generation < 1) {
            throw new IllegalArgumentException("멤버 활성 세대는 1 이상이어야 합니다.");
        }
        return generation;
    }

    private static long requireFairnessCredit(long fairnessCredit) {
        if (fairnessCredit < 0) {
            throw new IllegalArgumentException("공정성 크레딧은 음수일 수 없습니다.");
        }
        return fairnessCredit;
    }
}
