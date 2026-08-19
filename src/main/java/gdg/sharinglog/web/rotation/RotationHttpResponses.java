package gdg.sharinglog.web.rotation;

import gdg.sharinglog.service.rotation.api.idempotency.IdempotentResult;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

final class RotationHttpResponses {

    private RotationHttpResponses() {
    }

    static <T> ResponseEntity<T> from(IdempotentResult<T> result) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(result.status());
        if (result.etag() != null) {
            builder.header(HttpHeaders.ETAG, result.etag());
        }
        if (result.location() != null) {
            builder.header(HttpHeaders.LOCATION, result.location());
        }
        if (result.replayed()) {
            builder.header("Idempotency-Replayed", "true");
        }
        return builder.body(result.body());
    }
}
