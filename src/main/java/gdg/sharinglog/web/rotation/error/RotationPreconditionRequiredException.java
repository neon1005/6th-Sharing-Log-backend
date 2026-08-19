package gdg.sharinglog.web.rotation.error;

import java.util.Map;

import org.springframework.http.HttpStatus;

public final class RotationPreconditionRequiredException extends RotationApiException {

    public RotationPreconditionRequiredException(String detail) {
        this(detail, Map.of());
    }

    public RotationPreconditionRequiredException(String detail, Map<String, ?> properties) {
        super(
                HttpStatus.PRECONDITION_REQUIRED,
                RotationProblemCode.PRECONDITION_REQUIRED,
                detail,
                properties
        );
    }
}
