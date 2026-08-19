package gdg.sharinglog.service.booking.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class ReservationNotFoundException extends RuntimeException {

    public ReservationNotFoundException(String reservationPublicId) {
        super("예약을 찾을 수 없습니다: " + reservationPublicId);
    }
}
