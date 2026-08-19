package gdg.sharinglog.web.rotation.error;

import java.util.Map;

import org.springframework.http.HttpStatus;

public final class RotationConflictException extends RotationApiException {

    public RotationConflictException(RotationProblemCode problem, String detail) {
        this(problem, detail, Map.of());
    }

    public RotationConflictException(
            RotationProblemCode problem,
            String detail,
            Map<String, ?> properties) {
        super(HttpStatus.CONFLICT, problem, detail, properties);
    }
}
