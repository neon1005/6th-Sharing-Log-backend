package gdg.sharinglog.rotation.engine;

import java.util.Objects;

/**
 * A machine-readable reason and its human-readable audit detail.
 */
public record SelectionReason(SelectionReasonCode code, String detail) {

    public SelectionReason {
        Objects.requireNonNull(code, "code must not be null");
        if (detail == null || detail.isBlank()) {
            throw new IllegalArgumentException("detail must not be blank");
        }
    }
}
