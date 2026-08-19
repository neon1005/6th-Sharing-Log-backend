package gdg.sharinglog.service.rotation.exception;

public class OccurrenceNotFoundException extends RuntimeException {

    public OccurrenceNotFoundException(String occurrencePublicId) {
        super("업무 회차를 찾을 수 없습니다: " + occurrencePublicId);
    }
}
