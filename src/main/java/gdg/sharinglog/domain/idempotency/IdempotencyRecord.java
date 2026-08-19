package gdg.sharinglog.domain.idempotency;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

import gdg.sharinglog.domain.User;
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
        name = "idempotency_records",
        indexes = @Index(
                name = "idx_idempotency_records_expires_at",
                columnList = "expires_at"
        ),
        uniqueConstraints = @UniqueConstraint(
                name = "uk_idempotency_records_actor_method_uri_key",
                columnNames = {
                        "actor_user_id",
                        "http_method",
                        "uri_hash",
                        "idempotency_key"
                }
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Entity
public class IdempotencyRecord {

    private static final Pattern SHA_256_HEX = Pattern.compile("[0-9a-fA-F]{64}");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "actor_user_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_idempotency_records_actor_user")
    )
    private User actor;

    @Enumerated(EnumType.STRING)
    @Column(name = "http_method", nullable = false, length = 10)
    private IdempotencyHttpMethod httpMethod;

    @Column(name = "normalized_uri", nullable = false, length = 2048)
    private String normalizedUri;

    @Column(name = "uri_hash", nullable = false, length = 64)
    private String uriHash;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "response_status", nullable = false)
    private int responseStatus;

    @Lob
    @Column(name = "response_body", length = Length.LONG32)
    private String responseBody;

    @Column(name = "response_etag", length = 255)
    private String responseEtag;

    @Column(name = "response_location", length = 2048)
    private String responseLocation;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    private IdempotencyRecord(
            User actor,
            IdempotencyHttpMethod httpMethod,
            String normalizedUri,
            String uriHash,
            String idempotencyKey,
            String requestHash,
            int responseStatus,
            String responseBody,
            String responseEtag,
            String responseLocation,
            Instant createdAt,
            Instant expiresAt
    ) {
        this.actor = Objects.requireNonNull(actor, "Actor is required");
        this.httpMethod = Objects.requireNonNull(httpMethod, "HTTP method is required");
        this.normalizedUri = requireText(normalizedUri, "Normalized URI is required", 2048);
        this.uriHash = requireSha256(uriHash, "URI hash must be a SHA-256 hex value");
        this.idempotencyKey = requireText(
                idempotencyKey,
                "Idempotency key must contain between 8 and 128 characters",
                8,
                128
        );
        this.requestHash = requireSha256(
                requestHash,
                "Request hash must be a SHA-256 hex value"
        );
        if (responseStatus < 100 || responseStatus > 599) {
            throw new IllegalArgumentException("Response status must be between 100 and 599");
        }
        this.responseStatus = responseStatus;
        this.responseBody = responseBody;
        this.responseEtag = optionalText(responseEtag, "Response ETag", 255);
        this.responseLocation = optionalText(responseLocation, "Response location", 2048);
        this.createdAt = Objects.requireNonNull(createdAt, "Created time is required");
        this.expiresAt = Objects.requireNonNull(expiresAt, "Expiration time is required");
        if (!expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("Expiration time must be after creation time");
        }
    }

    public static IdempotencyRecord completed(
            User actor,
            IdempotencyHttpMethod httpMethod,
            String normalizedUri,
            String uriHash,
            String idempotencyKey,
            String requestHash,
            int responseStatus,
            String responseBody,
            String responseEtag,
            String responseLocation,
            Instant createdAt,
            Instant expiresAt
    ) {
        return new IdempotencyRecord(
                actor,
                httpMethod,
                normalizedUri,
                uriHash,
                idempotencyKey,
                requestHash,
                responseStatus,
                responseBody,
                responseEtag,
                responseLocation,
                createdAt,
                expiresAt
        );
    }

    public boolean isExpiredAt(Instant instant) {
        return !expiresAt.isAfter(Objects.requireNonNull(instant, "Reference time is required"));
    }

    private static String requireSha256(String value, String message) {
        String hash = Objects.requireNonNull(value, message);
        if (!SHA_256_HEX.matcher(hash).matches()) {
            throw new IllegalArgumentException(message);
        }
        return hash.toLowerCase(Locale.ROOT);
    }

    private static String requireText(String value, String message, int maxLength) {
        return requireText(value, message, 1, maxLength);
    }

    private static String requireText(
            String value,
            String message,
            int minLength,
            int maxLength
    ) {
        String required = Objects.requireNonNull(value, message);
        if (required.isBlank()
                || required.length() < minLength
                || required.length() > maxLength) {
            throw new IllegalArgumentException(message);
        }
        return required;
    }

    private static String optionalText(String value, String fieldName, int maxLength) {
        if (value == null) {
            return null;
        }
        if (value.isBlank() || value.length() > maxLength) {
            throw new IllegalArgumentException(
                    fieldName + " must be non-blank and at most " + maxLength + " characters"
            );
        }
        return value;
    }
}
