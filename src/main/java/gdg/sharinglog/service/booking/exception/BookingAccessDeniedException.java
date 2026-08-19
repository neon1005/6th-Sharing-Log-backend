package gdg.sharinglog.service.booking.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.FORBIDDEN)
public class BookingAccessDeniedException extends RuntimeException {

    public BookingAccessDeniedException() {
        super("이 그룹에 접근할 권한이 없습니다.");
    }

    public BookingAccessDeniedException(String message) {
        super(message);
    }
}
