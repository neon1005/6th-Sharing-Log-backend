package gdg.sharinglog.service.rotation.api.idempotency;

public record IdempotentResult<T>(
        int status,
        T body,
        String etag,
        String location,
        boolean replayed
) {
}
