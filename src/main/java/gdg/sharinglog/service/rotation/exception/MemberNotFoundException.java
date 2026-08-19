package gdg.sharinglog.service.rotation.exception;

public class MemberNotFoundException extends RuntimeException {

    public MemberNotFoundException(String memberPublicId) {
        super("그룹 멤버를 찾을 수 없습니다: " + memberPublicId);
    }
}
