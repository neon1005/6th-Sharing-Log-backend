package gdg.sharinglog.domain.push;

import java.time.Instant;
import java.util.Objects;

import gdg.sharinglog.domain.User;
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
        name = "push_subscriptions",
        indexes = @Index(name = "idx_push_subscriptions_user_id", columnList = "user_id"),
        uniqueConstraints = @UniqueConstraint(
                name = "uk_push_subscriptions_endpoint",
                columnNames = "endpoint"
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Entity
public class PushSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "user_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_push_subscriptions_user")
    )
    private User user;

    @Column(name = "endpoint", nullable = false, length = 512)
    private String endpoint;

    @Column(name = "p256dh", nullable = false, length = 255)
    private String p256dh;

    @Column(name = "auth", nullable = false, length = 255)
    private String auth;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public PushSubscription(
            User user,
            String endpoint,
            String p256dh,
            String auth,
            Instant createdAt
    ) {
        this.user = Objects.requireNonNull(user, "사용자는 필수입니다.");
        this.endpoint = requireText(endpoint, "endpoint는 필수입니다.");
        this.p256dh = requireText(p256dh, "p256dh 키는 필수입니다.");
        this.auth = requireText(auth, "auth 키는 필수입니다.");
        this.createdAt = Objects.requireNonNull(createdAt, "생성 시각은 필수입니다.");
    }

    public void refresh(User user, String p256dh, String auth) {
        this.user = Objects.requireNonNull(user, "사용자는 필수입니다.");
        this.p256dh = requireText(p256dh, "p256dh 키는 필수입니다.");
        this.auth = requireText(auth, "auth 키는 필수입니다.");
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
