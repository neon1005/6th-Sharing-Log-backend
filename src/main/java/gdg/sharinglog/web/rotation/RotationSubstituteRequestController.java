package gdg.sharinglog.web.rotation;

import java.time.Instant;

import gdg.sharinglog.domain.rotation.SubstituteRequestStatus;
import gdg.sharinglog.service.rotation.api.idempotency.CommandResponse;
import gdg.sharinglog.service.rotation.api.idempotency.IdempotentCommandExecutor;
import gdg.sharinglog.service.rotation.api.substitute.SubstituteRequestApplicationService;
import gdg.sharinglog.service.rotation.api.substitute.SubstituteRequestBox;
import gdg.sharinglog.web.rotation.dto.CreateSubstituteRequestRequest;
import gdg.sharinglog.web.rotation.dto.SubstituteRequestListResponse;
import gdg.sharinglog.web.rotation.dto.SubstituteRequestResponse;
import gdg.sharinglog.web.rotation.http.ExpectedVersion;
import gdg.sharinglog.web.rotation.http.IdempotencyKey;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/groups/{groupId}")
@RequiredArgsConstructor
public class RotationSubstituteRequestController {

    private final SubstituteRequestApplicationService service;
    private final IdempotentCommandExecutor idempotentExecutor;

    @PostMapping("/occurrences/{occurrenceId}/substitute-requests")
    public ResponseEntity<SubstituteRequestResponse> create(
            @PathVariable String groupId,
            @PathVariable String occurrenceId,
            @Valid @RequestBody CreateSubstituteRequestRequest body,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(name = "If-Match", required = false) String ifMatch,
            OAuth2AuthenticationToken authentication,
            HttpServletRequest request
    ) {
        ExpectedVersion expectedVersion = ExpectedVersion.parse(ifMatch);
        var result = idempotentExecutor.execute(
                authentication.getAuthorizedClientRegistrationId(),
                authentication.getPrincipal(),
                request.getMethod(),
                request.getRequestURI(),
                IdempotencyKey.parse(idempotencyKey),
                body,
                ifMatch,
                SubstituteRequestResponse.class,
                () -> {
                    SubstituteRequestResponse response = service.create(
                            groupId,
                            occurrenceId,
                            authentication.getAuthorizedClientRegistrationId(),
                            authentication.getPrincipal(),
                            expectedVersion.value(),
                            body.reason(),
                            Instant.now()
                    );
                    return new CommandResponse<>(
                            201,
                            response,
                            etag(response.version()),
                            "/api/groups/" + groupId
                                    + "/substitute-requests/" + response.requestId()
                    );
                }
        );
        return RotationHttpResponses.from(result);
    }

    @GetMapping("/substitute-requests")
    public SubstituteRequestListResponse findAll(
            @PathVariable String groupId,
            @RequestParam(defaultValue = "INBOX") SubstituteRequestBox box,
            @RequestParam(required = false) SubstituteRequestStatus status,
            OAuth2AuthenticationToken authentication
    ) {
        return service.findAll(
                groupId,
                authentication.getAuthorizedClientRegistrationId(),
                authentication.getPrincipal(),
                box,
                status
        );
    }

    @GetMapping("/substitute-requests/{requestId}")
    public SubstituteRequestResponse findOne(
            @PathVariable String groupId,
            @PathVariable String requestId,
            OAuth2AuthenticationToken authentication
    ) {
        return service.findOne(
                groupId,
                requestId,
                authentication.getAuthorizedClientRegistrationId(),
                authentication.getPrincipal()
        );
    }

    @PostMapping("/substitute-requests/{requestId}/accept")
    public ResponseEntity<SubstituteRequestResponse> accept(
            @PathVariable String groupId,
            @PathVariable String requestId,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(name = "If-Match", required = false) String ifMatch,
            OAuth2AuthenticationToken authentication,
            HttpServletRequest request
    ) {
        return respond(
                request,
                authentication,
                idempotencyKey,
                ifMatch,
                () -> service.accept(
                        groupId,
                        requestId,
                        authentication.getAuthorizedClientRegistrationId(),
                        authentication.getPrincipal(),
                        ExpectedVersion.parse(ifMatch).value(),
                        Instant.now()
                )
        );
    }

    @PostMapping("/substitute-requests/{requestId}/reject")
    public ResponseEntity<SubstituteRequestResponse> decline(
            @PathVariable String groupId,
            @PathVariable String requestId,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(name = "If-Match", required = false) String ifMatch,
            OAuth2AuthenticationToken authentication,
            HttpServletRequest request
    ) {
        return respond(
                request,
                authentication,
                idempotencyKey,
                ifMatch,
                () -> service.decline(
                        groupId,
                        requestId,
                        authentication.getAuthorizedClientRegistrationId(),
                        authentication.getPrincipal(),
                        ExpectedVersion.parse(ifMatch).value(),
                        Instant.now()
                )
        );
    }

    private ResponseEntity<SubstituteRequestResponse> respond(
            HttpServletRequest request,
            OAuth2AuthenticationToken authentication,
            String idempotencyKey,
            String ifMatch,
            java.util.function.Supplier<SubstituteRequestResponse> command
    ) {
        ExpectedVersion.parse(ifMatch);
        var result = idempotentExecutor.execute(
                authentication.getAuthorizedClientRegistrationId(),
                authentication.getPrincipal(),
                request.getMethod(),
                request.getRequestURI(),
                IdempotencyKey.parse(idempotencyKey),
                null,
                ifMatch,
                SubstituteRequestResponse.class,
                () -> {
                    SubstituteRequestResponse response = command.get();
                    return new CommandResponse<>(
                            200,
                            response,
                            etag(response.version()),
                            null
                    );
                }
        );
        return RotationHttpResponses.from(result);
    }

    private String etag(long version) {
        return new ExpectedVersion(version).toStrongEtag();
    }
}
