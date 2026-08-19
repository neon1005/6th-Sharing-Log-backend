package gdg.sharinglog.service.group.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.FORBIDDEN)
public class GroupMemberAccessDeniedException extends RuntimeException {

    public GroupMemberAccessDeniedException() {
        super("그룹 멤버만 멤버 목록을 조회할 수 있습니다.");
    }

    public GroupMemberAccessDeniedException(String message) {
        super(message);
    }
}
