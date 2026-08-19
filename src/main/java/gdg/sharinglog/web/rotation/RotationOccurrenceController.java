package gdg.sharinglog.web.rotation;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

import gdg.sharinglog.domain.rotation.ChoreFrequency;
import gdg.sharinglog.domain.rotation.OccurrenceStatus;
import gdg.sharinglog.service.rotation.api.idempotency.CommandResponse;
import gdg.sharinglog.service.rotation.api.idempotency.IdempotentCommandExecutor;
import gdg.sharinglog.service.rotation.api.occurrence.OccurrenceActionApplicationService;
import gdg.sharinglog.service.rotation.api.occurrence.OccurrenceQueryService;
import gdg.sharinglog.web.rotation.dto.OccurrenceActionRequest;
import gdg.sharinglog.web.rotation.dto.OccurrenceActionResponse;
import gdg.sharinglog.web.rotation.dto.CompletedOccurrenceHistoryResponse;
import gdg.sharinglog.web.rotation.dto.OccurrenceListResponse;
import gdg.sharinglog.web.rotation.dto.OccurrenceWeekPreviewResponse;
import gdg.sharinglog.web.rotation.dto.RetryAssignmentRequest;
import gdg.sharinglog.web.rotation.http.ExpectedVersion;
import gdg.sharinglog.web.rotation.http.IdempotencyKey;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/groups/{groupId}/occurrences")
@RequiredArgsConstructor
public class RotationOccurrenceController {

    private final OccurrenceQueryService queryService;
    private final OccurrenceActionApplicationService actionService;
    private final IdempotentCommandExecutor idempotentExecutor;

    @GetMapping
    public OccurrenceListResponse findActiveOn(
            @PathVariable String groupId,
            @RequestParam(required = false) ChoreFrequency frequency,
            @RequestParam(required = false) LocalDate activeOn,
            @RequestParam(name = "status", required = false) Set<OccurrenceStatus> statuses,
            @RequestParam(defaultValue = "false") boolean mineOnly,
            @RequestParam(required = false) String choreId,
            OAuth2AuthenticationToken authentication
    ) {
        return queryService.findActiveOn(
                groupId,
                authentication.getAuthorizedClientRegistrationId(),
                authentication.getPrincipal(),
                frequency,
                activeOn,
                statuses,
                mineOnly,
                choreId
        );
    }

    @PostMapping("/{occurrenceId}/complete")
    public ResponseEntity<OccurrenceActionResponse> complete(
            @PathVariable String groupId,
            @PathVariable String occurrenceId,
            @Valid @RequestBody(required = false) OccurrenceActionRequest body,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(name = "If-Match", required = false) String ifMatch,
            OAuth2AuthenticationToken authentication,
            HttpServletRequest request
    ) {
        OccurrenceActionRequest effectiveBody =
                body == null ? new OccurrenceActionRequest(null) : body;
        return executeAction(
                request,
                authentication,
                idempotencyKey,
                ifMatch,
                effectiveBody,
                () -> actionService.complete(
                        groupId,
                        occurrenceId,
                        authentication.getAuthorizedClientRegistrationId(),
                        authentication.getPrincipal(),
                        ExpectedVersion.parse(ifMatch).value(),
                        effectiveBody.note(),
                        Instant.now()
                )
        );
    }

    @GetMapping("/due-soon")        // 마감 임박
    public OccurrenceListResponse dueSoon(
            @PathVariable String groupId,
            OAuth2AuthenticationToken authentication
    ) {
        return queryService.findDueSoon(
                groupId,
                authentication.getAuthorizedClientRegistrationId(),
                authentication.getPrincipal()
        );
    }

    @GetMapping("/weekly-preview")
    public OccurrenceWeekPreviewResponse weeklyPreview(
            @PathVariable String groupId,
            @RequestParam(defaultValue = "0") @Min(0) @Max(4) int weekOffset,
            @RequestParam(required = false) ChoreFrequency frequency,
            OAuth2AuthenticationToken authentication
    ) {
        return queryService.findWeeklyPreview(
                groupId,
                authentication.getAuthorizedClientRegistrationId(),
                authentication.getPrincipal(),
                weekOffset,
                frequency
        );
    }

    @GetMapping("/completed-history")
    public CompletedOccurrenceHistoryResponse completedHistory(
            @PathVariable String groupId,
            @RequestParam(defaultValue = "false") boolean mineOnly,
            @RequestParam(required = false) String choreId,
            OAuth2AuthenticationToken authentication
    ) {
        return queryService.findCompletedHistory(
                groupId,
                authentication.getAuthorizedClientRegistrationId(),
                authentication.getPrincipal(),
                mineOnly,
                choreId
        );
    }

    @PostMapping("/{occurrenceId}/undo-complete")
    public ResponseEntity<OccurrenceActionResponse> undoComplete(
            @PathVariable String groupId,
            @PathVariable String occurrenceId,
            @Valid @RequestBody(required = false) OccurrenceActionRequest body,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(name = "If-Match", required = false) String ifMatch,
            OAuth2AuthenticationToken authentication,
            HttpServletRequest request
    ) {
        OccurrenceActionRequest effectiveBody =
                body == null ? new OccurrenceActionRequest(null) : body;
        return executeAction(
                request,
                authentication,
                idempotencyKey,
                ifMatch,
                effectiveBody,
                () -> actionService.undoCompletion(
                        groupId,
                        occurrenceId,
                        authentication.getAuthorizedClientRegistrationId(),
                        authentication.getPrincipal(),
                        ExpectedVersion.parse(ifMatch).value(),
                        effectiveBody.note(),
                        Instant.now()
                )
        );
    }

    @PostMapping("/{occurrenceId}/retry-assignment")
    public ResponseEntity<OccurrenceActionResponse> retryAssignment(
            @PathVariable String groupId,
            @PathVariable String occurrenceId,
            @Valid @RequestBody RetryAssignmentRequest body,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(name = "If-Match", required = false) String ifMatch,
            OAuth2AuthenticationToken authentication,
            HttpServletRequest request
    ) {
        return executeAction(
                request,
                authentication,
                idempotencyKey,
                ifMatch,
                body,
                () -> actionService.retryAssignment(
                        groupId,
                        occurrenceId,
                        authentication.getAuthorizedClientRegistrationId(),
                        authentication.getPrincipal(),
                        ExpectedVersion.parse(ifMatch).value(),
                        body,
                        Instant.now()
                )
        );
    }

    private ResponseEntity<OccurrenceActionResponse> executeAction(
            HttpServletRequest request,
            OAuth2AuthenticationToken authentication,
            String idempotencyKey,
            String ifMatch,
            Object body,
            java.util.function.Supplier<OccurrenceActionResponse> action
    ) {
        ExpectedVersion.parse(ifMatch);
        var result = idempotentExecutor.execute(
                authentication.getAuthorizedClientRegistrationId(),
                authentication.getPrincipal(),
                request.getMethod(),
                request.getRequestURI(),
                IdempotencyKey.parse(idempotencyKey),
                body,
                ifMatch,
                OccurrenceActionResponse.class,
                () -> {
                    OccurrenceActionResponse response = action.get();
                    return new CommandResponse<>(
                            200,
                            response,
                            new ExpectedVersion(response.occurrence().version()).toStrongEtag(),
                            null
                    );
                }
        );
        return RotationHttpResponses.from(result);
    }
}
