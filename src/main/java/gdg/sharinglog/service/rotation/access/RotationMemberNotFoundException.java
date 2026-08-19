package gdg.sharinglog.service.rotation.access;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class RotationMemberNotFoundException extends RuntimeException {

    public RotationMemberNotFoundException(String membershipPublicId) {
        super("Active group member was not found: " + membershipPublicId);
    }
}
