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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Table(
        name = "substitute_request_recipients",
        indexes = @Index(
                name = "idx_substitute_recipients_member_status",
                columnList = "membership_id, response_status, request_id"
        ),
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_substitute_recipients_request_member",
                        columnNames = {"request_id", "membership_id"}
                ),
                @UniqueConstraint(
                        name = "uk_substitute_recipients_one_accepted",
                        columnNames = {"request_id", "accepted_marker"}
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Entity
public class SubstituteRequestRecipient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "request_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_substitute_recipients_request")
    )
    private SubstituteRequest request;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "membership_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_substitute_recipients_membership")
    )
    private GroupMember member;

    @Column(name = "member_activation_generation", nullable = false)
    private long memberActivationGeneration;

    @Enumerated(EnumType.STRING)
    @Column(name = "response_status", nullable = false, length = 20)
    private SubstituteRecipientStatus responseStatus;

    @Column(name = "responded_at")
    private Instant respondedAt;

    @Column(name = "accepted_marker")
    private Integer acceptedMarker;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    public SubstituteRequestRecipient(
            SubstituteRequest request,
            GroupMember member
    ) {
        this.request = Objects.requireNonNull(request, "대타 요청은 필수입니다.");
        this.member = Objects.requireNonNull(member, "대타 요청 대상 멤버는 필수입니다.");
        if (member.getGroup() != request.getOccurrence().getChore().getGroup()
                || member.getId().equals(request.requester().getId())) {
            throw new IllegalArgumentException("대타 요청 대상 멤버가 올바르지 않습니다.");
        }
        if (!member.isActive()) {
            throw new IllegalArgumentException("활성 멤버만 대타 요청을 받을 수 있습니다.");
        }
        this.memberActivationGeneration = member.getActivationGeneration();
        this.responseStatus = SubstituteRecipientStatus.PENDING;
    }

    public boolean belongsToCurrentActivation() {
        return member.isActive()
                && memberActivationGeneration == member.getActivationGeneration();
    }

    public void accept(Instant acceptedAt) {
        requirePendingAndCurrent();
        this.responseStatus = SubstituteRecipientStatus.ACCEPTED;
        this.respondedAt = Objects.requireNonNull(acceptedAt, "수락 시각은 필수입니다.");
        this.acceptedMarker = 1;
    }

    public void decline(Instant declinedAt) {
        requirePendingAndCurrent();
        this.responseStatus = SubstituteRecipientStatus.DECLINED;
        this.respondedAt = Objects.requireNonNull(declinedAt, "거절 시각은 필수입니다.");
    }

    public void markIneligible(Instant changedAt) {
        if (!responseStatus.isPending()) {
            return;
        }
        this.responseStatus = SubstituteRecipientStatus.INELIGIBLE;
        this.respondedAt = Objects.requireNonNull(changedAt, "대상 제외 시각은 필수입니다.");
    }

    private void requirePendingAndCurrent() {
        if (!responseStatus.isPending()) {
            throw new IllegalStateException("이미 응답한 대타 요청입니다.");
        }
        if (!belongsToCurrentActivation()) {
            throw new IllegalStateException("현재 활성 세대의 멤버만 응답할 수 있습니다.");
        }
    }
}
