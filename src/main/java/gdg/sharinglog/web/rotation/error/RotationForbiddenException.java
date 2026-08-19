package gdg.sharinglog.web.rotation.error;

import java.util.Map;

import org.springframework.http.HttpStatus;

public final class RotationForbiddenException extends RotationApiException {

    public RotationForbiddenException(String detail) {
        this(RotationProblemCode.FORBIDDEN, detail, Map.of());
    }

    public RotationForbiddenException(RotationProblemCode problem, String detail) {
        this(problem, detail, Map.of());
    }

    public RotationForbiddenException(
            RotationProblemCode problem,
            String detail,
            Map<String, ?> properties) {
        super(HttpStatus.FORBIDDEN, problem, detail, properties);
    }
}
