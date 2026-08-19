package gdg.sharinglog.service.rotation.access;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.FORBIDDEN)
public class RotationAccessDeniedException extends RuntimeException {

    public RotationAccessDeniedException() {
        super("An active group membership is required.");
    }

    public RotationAccessDeniedException(String message) {
        super(message);
    }
}
