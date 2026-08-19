package gdg.sharinglog.web.rotation.error;

import java.util.Map;

import org.springframework.http.HttpStatus;

public final class RotationBadRequestException extends RotationApiException {

    public RotationBadRequestException(RotationProblemCode problem, String detail) {
        this(problem, detail, Map.of());
    }

    public RotationBadRequestException(
            RotationProblemCode problem,
            String detail,
            Map<String, ?> properties) {
        super(HttpStatus.BAD_REQUEST, problem, detail, properties);
    }
}
