package gdg.sharinglog.service.group.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class GroupMemberNotFoundException extends RuntimeException {

    public GroupMemberNotFoundException(String membershipPublicId) {
        super("멤버를 찾을 수 없습니다: " + membershipPublicId);
    }
}
