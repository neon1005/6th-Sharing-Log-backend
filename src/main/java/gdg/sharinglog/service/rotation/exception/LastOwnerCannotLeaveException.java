package gdg.sharinglog.service.rotation.exception;

public class LastOwnerCannotLeaveException extends RuntimeException {

    public LastOwnerCannotLeaveException() {
        super("그룹의 마지막 소유자는 탈퇴할 수 없습니다.");
    }
}
