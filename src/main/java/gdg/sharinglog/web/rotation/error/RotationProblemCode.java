package gdg.sharinglog.web.rotation.error;

import java.net.URI;
import java.util.Locale;

import org.springframework.http.HttpStatus;

public enum RotationProblemCode {

    VALIDATION_FAILED(
            HttpStatus.BAD_REQUEST,
            "요청 값이 올바르지 않습니다.",
            "요청 필드를 확인해 주세요."
    ),
    INVALID_QUERY(
            HttpStatus.BAD_REQUEST,
            "조회 조건이 올바르지 않습니다.",
            "쿼리 파라미터를 확인해 주세요."
    ),
    UNAUTHENTICATED(
            HttpStatus.UNAUTHORIZED,
            "로그인이 필요합니다.",
            "로그인한 뒤 다시 시도해 주세요."
    ),
    FORBIDDEN(
            HttpStatus.FORBIDDEN,
            "요청을 수행할 권한이 없습니다.",
            "현재 계정의 그룹 권한을 확인해 주세요."
    ),
    NOT_CURRENT_ASSIGNEE(
            HttpStatus.FORBIDDEN,
            "현재 담당자만 수행할 수 있습니다.",
            "이 회차의 현재 담당자를 확인해 주세요."
    ),
    RESOURCE_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "요청한 리소스를 찾을 수 없습니다.",
            "리소스 식별자와 소속 그룹을 확인해 주세요."
    ),
    VERSION_CONFLICT(
            HttpStatus.CONFLICT,
            "리소스 버전이 변경되었습니다.",
            "리소스를 다시 조회한 뒤 요청을 다시 시도해 주세요."
    ),
    CHORE_VERSION_CONFLICT(
            HttpStatus.CONFLICT,
            "업무 버전이 변경되었습니다.",
            "업무를 다시 조회한 뒤 요청을 다시 시도해 주세요."
    ),
    INVALID_OCCURRENCE_STATE(
            HttpStatus.CONFLICT,
            "현재 회차 상태에서는 요청을 수행할 수 없습니다.",
            "회차의 최신 상태를 확인해 주세요."
    ),
    INVALID_SUBSTITUTE_REQUEST_STATE(
            HttpStatus.CONFLICT,
            "현재 대타 요청 상태에서는 처리할 수 없습니다.",
            "대타 요청과 회차의 최신 상태를 확인해 주세요."
    ),
    SUBSTITUTE_REQUEST_ALREADY_EXISTS(
            HttpStatus.CONFLICT,
            "이미 진행 중인 대타 요청이 있습니다.",
            "기존 대타 요청의 응답 상태를 확인해 주세요."
    ),
    SUBSTITUTE_REQUESTED_BY_ANOTHER_MEMBER(
            HttpStatus.FORBIDDEN,
            "다른 사용자가 올린 대타 요청입니다",
            "다른 사용자가 올린 대타 요청입니다"
    ),
    NO_SUBSTITUTE_CANDIDATE(
            HttpStatus.CONFLICT,
            "대타 요청을 받을 수 있는 멤버가 없습니다.",
            "현재 회차의 가능 멤버를 확인해 주세요."
    ),
    NOT_SUBSTITUTE_RECIPIENT(
            HttpStatus.FORBIDDEN,
            "이 대타 요청에 응답할 수 없습니다.",
            "현재 계정이 요청 대상인지 확인해 주세요."
    ),
    IDEMPOTENCY_KEY_REUSED(
            HttpStatus.CONFLICT,
            "멱등 키가 다른 요청에 이미 사용되었습니다.",
            "새 Idempotency-Key로 요청해 주세요."
    ),
    MEMBER_ALREADY_LEFT(
            HttpStatus.CONFLICT,
            "이미 탈퇴한 멤버입니다.",
            "멤버의 최신 상태를 확인해 주세요."
    ),
    LAST_OWNER_CANNOT_LEAVE(
            HttpStatus.CONFLICT,
            "마지막 소유자는 그룹을 탈퇴할 수 없습니다.",
            "다른 활성 멤버에게 소유자 권한을 이전한 뒤 다시 시도해 주세요."
    ),
    PRECONDITION_REQUIRED(
            HttpStatus.PRECONDITION_REQUIRED,
            "요청 전제 조건이 필요합니다.",
            "기존 리소스를 변경하려면 If-Match 헤더를 보내 주세요."
    );

    private static final String TYPE_BASE = "https://sharing-log.example/problems/";

    private final HttpStatus status;
    private final String title;
    private final String defaultDetail;
    private final URI type;

    RotationProblemCode(HttpStatus status, String title, String defaultDetail) {
        this.status = status;
        this.title = title;
        this.defaultDetail = defaultDetail;
        this.type = URI.create(TYPE_BASE + name().toLowerCase(Locale.ROOT).replace('_', '-'));
    }

    public HttpStatus status() {
        return status;
    }

    public String title() {
        return title;
    }

    public String defaultDetail() {
        return defaultDetail;
    }

    public URI type() {
        return type;
    }
}
