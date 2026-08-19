package gdg.sharinglog.web.rotation;

import java.time.Instant;
import java.util.List;

import gdg.sharinglog.domain.rotation.ChoreFrequency;
import gdg.sharinglog.service.rotation.api.chore.ChoreApplicationService;
import gdg.sharinglog.service.rotation.api.chore.CreateChoreCommand;
import gdg.sharinglog.service.rotation.api.chore.UpdateChoreCommand;
import gdg.sharinglog.service.rotation.api.idempotency.CommandResponse;
import gdg.sharinglog.service.rotation.api.idempotency.IdempotentCommandExecutor;
import gdg.sharinglog.web.rotation.dto.ChoreListResponse;
import gdg.sharinglog.web.rotation.dto.CreateChoreRequest;
import gdg.sharinglog.web.rotation.dto.CreateChoreResponse;
import gdg.sharinglog.web.rotation.dto.ChoreResponse;
import gdg.sharinglog.web.rotation.dto.UpdateChoreRequest;
import gdg.sharinglog.web.rotation.error.RotationBadRequestException;
import gdg.sharinglog.web.rotation.error.RotationProblemCode;
import gdg.sharinglog.web.rotation.http.ExpectedVersion;
import gdg.sharinglog.web.rotation.http.IdempotencyKey;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/groups/{groupId}/chores")
@RequiredArgsConstructor
public class RotationChoreController {

    private final ChoreApplicationService choreService;
    private final IdempotentCommandExecutor idempotentExecutor;
    private final RotationViewMapper viewMapper;

    @PostMapping
    public ResponseEntity<CreateChoreResponse> create(
            @PathVariable String groupId,
            @Valid @RequestBody CreateChoreRequest request,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            OAuth2AuthenticationToken authentication,
            HttpServletRequest servletRequest
    ) {
        var result = idempotentExecutor.execute(
                authentication.getAuthorizedClientRegistrationId(),
                authentication.getPrincipal(),
                "POST",
                servletRequest.getRequestURI(),
                IdempotencyKey.parse(idempotencyKey),
                request,
                null,
                CreateChoreResponse.class,
                () -> {
                    var created = choreService.create(
                            groupId,
                            authentication.getAuthorizedClientRegistrationId(),
                            authentication.getPrincipal(),
                            toCommand(request),
                            Instant.now()
                    );
                    var body = new CreateChoreResponse(
                            viewMapper.chore(created.chore()),
                            viewMapper.occurrence(created.currentOccurrence(), created.actor())
                    );
                    return new CommandResponse<>(
                            201,
                            body,
                            etag(body.chore().version()),
                            "/api/groups/" + groupId + "/chores/" + body.chore().choreId()
                    );
                }
        );
        return RotationHttpResponses.from(result);
    }

    @GetMapping
    public ChoreListResponse findAll(
            @PathVariable String groupId,
            @RequestParam(required = false) ChoreFrequency frequency,
            @RequestParam(defaultValue = "true") String active,
            OAuth2AuthenticationToken authentication
    ) {
        var items = choreService.findAll(
                        groupId,
                        authentication.getAuthorizedClientRegistrationId(),
                        authentication.getPrincipal(),
                        frequency,
                        parseActive(active)
                )
                .stream()
                .map(viewMapper::chore)
                .toList();
        return new ChoreListResponse(items, null, false);
    }

    @PatchMapping("/{choreId}")
    public ResponseEntity<ChoreResponse> update(
            @PathVariable String groupId,
            @PathVariable String choreId,
            @Valid @RequestBody UpdateChoreRequest request,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(name = "If-Match", required = false) String ifMatch,
            OAuth2AuthenticationToken authentication,
            HttpServletRequest servletRequest
    ) {
        ExpectedVersion expectedVersion = ExpectedVersion.parse(ifMatch);
        var result = idempotentExecutor.execute(
                authentication.getAuthorizedClientRegistrationId(),
                authentication.getPrincipal(),
                servletRequest.getMethod(),
                servletRequest.getRequestURI(),
                IdempotencyKey.parse(idempotencyKey),
                request,
                ifMatch,
                ChoreResponse.class,
                () -> {
                    var updated = choreService.update(
                            groupId,
                            choreId,
                            authentication.getAuthorizedClientRegistrationId(),
                            authentication.getPrincipal(),
                            toCommand(request),
                            expectedVersion.value(),
                            Instant.now()
                    );
                    ChoreResponse body = viewMapper.chore(updated);
                    return new CommandResponse<>(
                            200,
                            body,
                            etag(body.version()),
                            null
                    );
                }
        );
        return RotationHttpResponses.from(result);
    }

    @DeleteMapping("/{choreId}")
    public ResponseEntity<Void> deactivate(
            @PathVariable String groupId,
            @PathVariable String choreId,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(name = "If-Match", required = false) String ifMatch,
            OAuth2AuthenticationToken authentication,
            HttpServletRequest servletRequest
    ) {
        ExpectedVersion expectedVersion = ExpectedVersion.parse(ifMatch);
        var result = idempotentExecutor.execute(
                authentication.getAuthorizedClientRegistrationId(),
                authentication.getPrincipal(),
                servletRequest.getMethod(),
                servletRequest.getRequestURI(),
                IdempotencyKey.parse(idempotencyKey),
                null,
                ifMatch,
                Void.class,
                () -> {
                    long version = choreService.deactivate(
                            groupId,
                            choreId,
                            authentication.getAuthorizedClientRegistrationId(),
                            authentication.getPrincipal(),
                            expectedVersion.value(),
                            Instant.now()
                    );
                    return new CommandResponse<>(
                            204,
                            null,
                            etag(version),
                            null
                    );
                }
        );
        return RotationHttpResponses.from(result);
    }

    private CreateChoreCommand toCommand(CreateChoreRequest request) {
        return new CreateChoreCommand(
                request.name(),
                request.schedule().frequency(),
                request.schedule().dueTime(),
                request.schedule().weeklyDueDay(),
                request.schedule().biweeklyAnchorDate(),
                request.eligibility().mode(),
                request.eligibility().membershipIds()
        );
    }

    private UpdateChoreCommand toCommand(UpdateChoreRequest request) {
        UpdateChoreCommand.Schedule schedule = request.schedule() == null
                ? null
                : new UpdateChoreCommand.Schedule(
                        request.schedule().frequency(),
                        request.schedule().dueTime(),
                        request.schedule().weeklyDueDay(),
                        request.schedule().biweeklyAnchorDate()
                );
        UpdateChoreCommand.Eligibility eligibility = request.eligibility() == null
                ? null
                : new UpdateChoreCommand.Eligibility(
                        request.eligibility().mode(),
                        request.eligibility().membershipIds()
                );
        return new UpdateChoreCommand(request.name(), schedule, eligibility);
    }

    private Boolean parseActive(String value) {
        return switch (value.toLowerCase(java.util.Locale.ROOT)) {
            case "true" -> true;
            case "false" -> false;
            case "all" -> null;
            default -> throw new RotationBadRequestException(
                    RotationProblemCode.INVALID_QUERY,
                    "active must be true, false, or all."
            );
        };
    }

    private String etag(long version) {
        return new ExpectedVersion(version).toStrongEtag();
    }
}
