package gdg.sharinglog.service.invitation.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.FORBIDDEN)
public class InvitationPermissionDeniedException extends RuntimeException {

    public InvitationPermissionDeniedException() {
        super("그룹 OWNER만 초대 코드를 발급할 수 있습니다.");
    }
}
