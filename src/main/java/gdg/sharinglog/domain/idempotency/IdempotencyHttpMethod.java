package gdg.sharinglog.domain.idempotency;

import java.util.Locale;
import java.util.Objects;

public enum IdempotencyHttpMethod {
    GET,
    HEAD,
    POST,
    PUT,
    PATCH,
    DELETE,
    OPTIONS,
    TRACE,
    CONNECT;

    public static IdempotencyHttpMethod from(String value) {
        String method = Objects.requireNonNull(value, "HTTP method is required").trim();
        if (method.isEmpty()) {
            throw new IllegalArgumentException("HTTP method is required");
        }
        return valueOf(method.toUpperCase(Locale.ROOT));
    }
}
