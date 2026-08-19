package gdg.sharinglog.service.rotation.exception;

public class ChoreNotFoundException extends RuntimeException {

    public ChoreNotFoundException(Long choreId) {
        super("업무를 찾을 수 없습니다: " + choreId);
    }
}
