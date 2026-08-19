package gdg.sharinglog.service.invitation.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class InvitationNotFoundException extends RuntimeException {

    public InvitationNotFoundException() {
        super("초대 링크를 찾을 수 없습니다.");
    }
}
