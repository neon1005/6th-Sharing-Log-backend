package gdg.sharinglog.web.rotation.error;

import java.util.Map;

import org.springframework.http.HttpStatus;

public final class RotationNotFoundException extends RotationApiException {

    public RotationNotFoundException(String detail) {
        this(detail, Map.of());
    }

    public RotationNotFoundException(String detail, Map<String, ?> properties) {
        super(
                HttpStatus.NOT_FOUND,
                RotationProblemCode.RESOURCE_NOT_FOUND,
                detail,
                properties
        );
    }
}
