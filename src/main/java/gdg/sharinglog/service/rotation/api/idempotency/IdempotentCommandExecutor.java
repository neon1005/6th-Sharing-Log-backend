package gdg.sharinglog.service.rotation.api.idempotency;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.function.Supplier;

import gdg.sharinglog.domain.User;
import gdg.sharinglog.domain.idempotency.IdempotencyHttpMethod;
import gdg.sharinglog.domain.idempotency.IdempotencyRecord;
import gdg.sharinglog.repository.idempotency.IdempotencyRecordRepository;
import gdg.sharinglog.service.user.AuthenticatedUserService;
import gdg.sharinglog.web.rotation.error.RotationConflictException;
import gdg.sharinglog.web.rotation.error.RotationProblemCode;
import gdg.sharinglog.web.rotation.http.IdempotencyKey;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
@RequiredArgsConstructor
public class IdempotentCommandExecutor {

    private static final Duration RETENTION = Duration.ofHours(24);

    private final AuthenticatedUserService authenticatedUserService;
    private final IdempotencyRecordRepository recordRepository;
    private final JsonMapper jsonMapper;

    @Transactional
    public <T> IdempotentResult<T> execute(
            String registrationId,
            OAuth2User principal,
            String httpMethod,
            String normalizedUri,
            IdempotencyKey idempotencyKey,
            Object requestBody,
            String ifMatch,
            Class<T> responseType,
            Supplier<CommandResponse<T>> command
    ) {
        Objects.requireNonNull(principal, "OAuth2 사용자는 필수입니다.");
        Objects.requireNonNull(idempotencyKey, "멱등 키는 필수입니다.");
        Objects.requireNonNull(responseType, "응답 타입은 필수입니다.");
        Objects.requireNonNull(command, "명령은 필수입니다.");

        User actor = authenticatedUserService.requireUserForUpdate(registrationId, principal);
        IdempotencyHttpMethod method = IdempotencyHttpMethod.from(httpMethod);
        String uri = normalizeUri(normalizedUri);
        String uriHash = sha256(uri);
        String requestHash = sha256(fingerprint(requestBody, ifMatch));
        Instant now = Instant.now();

        var existing = recordRepository.findByIdentityForUpdate(
                actor.getId(),
                method,
                uriHash,
                idempotencyKey.value()
        );
        if (existing.isPresent()) {
            IdempotencyRecord record = existing.get();
            if (record.isExpiredAt(now)) {
                recordRepository.delete(record);
                recordRepository.flush();
            } else {
                requireSameRequest(record, uri, requestHash);
                return replay(record, responseType);
            }
        }

        CommandResponse<T> response = Objects.requireNonNull(
                command.get(),
                "명령 응답은 필수입니다."
        );
        String responseBody = serializeBody(response.body());
        recordRepository.saveAndFlush(IdempotencyRecord.completed(
                actor,
                method,
                uri,
                uriHash,
                idempotencyKey.value(),
                requestHash,
                response.status(),
                responseBody,
                response.etag(),
                response.location(),
                now,
                now.plus(RETENTION)
        ));

        return new IdempotentResult<>(
                response.status(),
                response.body(),
                response.etag(),
                response.location(),
                false
        );
    }

    private String fingerprint(Object requestBody, String ifMatch) {
        String body = requestBody == null ? "" : writeJson(requestBody);
        String condition = ifMatch == null ? "" : ifMatch.trim();
        return condition + "\n" + body;
    }

    private void requireSameRequest(
            IdempotencyRecord record,
            String normalizedUri,
            String requestHash
    ) {
        if (!record.getNormalizedUri().equals(normalizedUri)
                || !record.getRequestHash().equals(requestHash)) {
            throw new RotationConflictException(
                    RotationProblemCode.IDEMPOTENCY_KEY_REUSED,
                    "같은 Idempotency-Key가 다른 본문 또는 If-Match 값에 사용되었습니다."
            );
        }
    }

    private <T> IdempotentResult<T> replay(
            IdempotencyRecord record,
            Class<T> responseType
    ) {
        T body = null;
        if (record.getResponseBody() != null) {
            try {
                body = jsonMapper.readValue(record.getResponseBody(), responseType);
            } catch (Exception exception) {
                throw new IllegalStateException("저장된 멱등 응답을 복원하지 못했습니다.", exception);
            }
        }
        return new IdempotentResult<>(
                record.getResponseStatus(),
                body,
                record.getResponseEtag(),
                record.getResponseLocation(),
                true
        );
    }

    private String serializeBody(Object body) {
        return body == null ? null : writeJson(body);
    }

    private String writeJson(Object value) {
        try {
            return jsonMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("멱등 응답 JSON을 직렬화하지 못했습니다.", exception);
        }
    }

    private String normalizeUri(String value) {
        String uri = Objects.requireNonNull(value, "정규화 URI는 필수입니다.").trim();
        if (!uri.startsWith("/") || uri.length() > 2048 || uri.contains("?")) {
            throw new IllegalArgumentException("정규화 URI는 쿼리 없는 절대 경로여야 합니다.");
        }
        return uri.replaceAll("/{2,}", "/");
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest
                    .getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
    }
}
