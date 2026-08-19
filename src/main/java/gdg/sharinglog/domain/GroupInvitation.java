package gdg.sharinglog.domain;

import java.time.Instant;
import java.util.Objects;

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
        name = "group_invitations",
        indexes = {
                @Index(name = "idx_group_invitations_group_id", columnList = "group_id"),
                @Index(name = "idx_group_invitations_expires_at", columnList = "expires_at")
        },
        uniqueConstraints = @UniqueConstraint(
                name = "uk_group_invitations_code_hash",
                columnNames = "code_hash"
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Entity
public class GroupInvitation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "group_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_group_invitations_group")
    )
    private SharingGroup group;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "created_by_user_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_group_invitations_created_by_user")
    )
    private User createdBy;

    @Column(name = "code_hash", nullable = false, length = 64)
    private String codeHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    public GroupInvitation(SharingGroup group, User createdBy, String codeHash,
                           Instant createdAt, Instant expiresAt) {
        this.group = Objects.requireNonNull(group, "그룹은 필수입니다.");
        this.createdBy = Objects.requireNonNull(createdBy, "초대 생성자는 필수입니다.");
        this.codeHash = Objects.requireNonNull(codeHash, "초대 코드 해시는 필수입니다.");
        this.createdAt = Objects.requireNonNull(createdAt, "초대 생성 시각은 필수입니다.");
        this.expiresAt = Objects.requireNonNull(expiresAt, "초대 만료 시각은 필수입니다.");
    }

    public boolean isUsableAt(Instant now) {
        return revokedAt == null && now.isBefore(expiresAt);
    }

    public void revoke(Instant revokedAt) {
        this.revokedAt = Objects.requireNonNull(revokedAt, "초대 취소 시각은 필수입니다.");
    }
}
