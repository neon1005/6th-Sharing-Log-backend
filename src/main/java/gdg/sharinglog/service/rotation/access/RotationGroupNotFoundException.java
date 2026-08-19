package gdg.sharinglog.service.rotation.access;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class RotationGroupNotFoundException extends RuntimeException {

    public RotationGroupNotFoundException(String groupPublicId) {
        super("Group was not found: " + groupPublicId);
    }
}
