package gdg.sharinglog.service.booking.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class BookingGroupNotFoundException extends RuntimeException {

    public BookingGroupNotFoundException(String groupPublicId) {
        super("그룹을 찾을 수 없습니다: " + groupPublicId);
    }
}
