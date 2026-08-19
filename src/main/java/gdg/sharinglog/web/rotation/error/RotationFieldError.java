package gdg.sharinglog.web.rotation.error;

import java.util.Objects;

public record RotationFieldError(String field, String reason) {

    public RotationFieldError {
        field = requireText(field, "field");
        reason = requireText(reason, "reason");
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
