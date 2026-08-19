package gdg.sharinglog.service.booking.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class SpaceNotFoundException extends RuntimeException {

    public SpaceNotFoundException(String spacePublicId) {
        super("공간을 찾을 수 없습니다: " + spacePublicId);
    }
}
