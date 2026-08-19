package gdg.sharinglog.service.invitation.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.GONE)
public class InvitationUnavailableException extends RuntimeException {

    public InvitationUnavailableException() {
        super("만료되었거나 취소된 초대 링크입니다.");
    }
}
