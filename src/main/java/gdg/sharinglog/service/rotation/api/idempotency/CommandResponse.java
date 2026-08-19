package gdg.sharinglog.service.rotation.api.idempotency;

public record CommandResponse<T>(
        int status,
        T body,
        String etag,
        String location
) {

    public CommandResponse {
        if (status < 200 || status > 299) {
            throw new IllegalArgumentException("멱등 저장 대상은 성공 응답이어야 합니다.");
        }
    }
}
