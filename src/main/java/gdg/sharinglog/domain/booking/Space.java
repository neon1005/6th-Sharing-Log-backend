package gdg.sharinglog.domain.booking;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import gdg.sharinglog.domain.SharingGroup;
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
        name = "spaces",
        indexes = @Index(name = "idx_spaces_group_id", columnList = "group_id"),
        uniqueConstraints = @UniqueConstraint(
                name = "uk_spaces_group_name",
                columnNames = {"group_id", "name"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Entity
public class Space {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false)
    private Long id;

    @Column(name = "public_id", nullable = false, updatable = false, unique = true, length = 36)
    private String publicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "group_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_spaces_group")
    )
    private SharingGroup group;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Space(SharingGroup group, String name, Instant createdAt) {
        this.group = Objects.requireNonNull(group, "그룹은 필수입니다.");
        this.name = requireName(name);
        this.publicId = UUID.randomUUID().toString();
        this.active = true;
        this.createdAt = Objects.requireNonNull(createdAt, "생성 시각은 필수입니다.");
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("공간 이름은 필수입니다.");
        }
        return name.trim();
    }

    public void deactivate() {
        this.active = false;
    }
}
