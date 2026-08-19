package gdg.sharinglog.web.rotation;

import java.time.Instant;

import gdg.sharinglog.service.rotation.api.idempotency.CommandResponse;
import gdg.sharinglog.service.rotation.api.idempotency.IdempotentCommandExecutor;
import gdg.sharinglog.service.rotation.api.member.ChoreParticipationApplicationService;
import gdg.sharinglog.service.rotation.api.member.MemberLeaveApplicationService;
import gdg.sharinglog.service.rotation.api.member.RotationMemberQueryService;
import gdg.sharinglog.service.rotation.api.member.UpdateChoreParticipationsCommand;
import gdg.sharinglog.service.rotation.api.member.UpdatedChoreParticipations;
import gdg.sharinglog.web.rotation.dto.MemberLeaveResponse;
import gdg.sharinglog.web.rotation.dto.RotationMemberListResponse;
import gdg.sharinglog.web.rotation.dto.UpdateChoreParticipationsRequest;
import gdg.sharinglog.web.rotation.dto.UpdateChoreParticipationsResponse;
import gdg.sharinglog.web.rotation.http.ExpectedVersion;
import gdg.sharinglog.web.rotation.http.IdempotencyKey;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/groups/{groupId}")
@RequiredArgsConstructor
public class RotationMemberController {

    private final MemberLeaveApplicationService leaveService;
    private final RotationMemberQueryService queryService;
    private final ChoreParticipationApplicationService participationService;
    private final IdempotentCommandExecutor idempotentExecutor;

    @GetMapping("/rotation-members")
    public RotationMemberListResponse activeMembers(
            @PathVariable String groupId,
            OAuth2AuthenticationToken authentication
    ) {
        return queryService.findActiveMembers(
                groupId,
                authentication.getAuthorizedClientRegistrationId(),
                authentication.getPrincipal()
        );
    }

    @PostMapping("/members/{membershipId}/leave")
    public ResponseEntity<MemberLeaveResponse> leave(
            @PathVariable String groupId,
            @PathVariable String membershipId,
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
                null,
                ifMatch,
                MemberLeaveResponse.class,
                () -> {
                    MemberLeaveResponse response = leaveService.leave(
                            groupId,
                            membershipId,
                            authentication.getAuthorizedClientRegistrationId(),
                            authentication.getPrincipal(),
                            expectedVersion.value(),
                            Instant.now()
                    );
                    return new CommandResponse<>(
                            200,
                            response,
                            new ExpectedVersion(response.member().version()).toStrongEtag(),
                            null
                    );
                }
        );
        return RotationHttpResponses.from(result);
    }

    @PatchMapping("/rotation-members/{membershipId}/chore-participations")
    public ResponseEntity<UpdateChoreParticipationsResponse> updateChoreParticipations(
            @PathVariable String groupId,
            @PathVariable String membershipId,
            @Valid @RequestBody UpdateChoreParticipationsRequest request,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            OAuth2AuthenticationToken authentication,
            HttpServletRequest servletRequest
    ) {
        var result = idempotentExecutor.execute(
                authentication.getAuthorizedClientRegistrationId(),
                authentication.getPrincipal(),
                servletRequest.getMethod(),
                servletRequest.getRequestURI(),
                IdempotencyKey.parse(idempotencyKey),
                request,
                null,
                UpdateChoreParticipationsResponse.class,
                () -> {
                    UpdatedChoreParticipations updated = participationService.update(
                            groupId,
                            membershipId,
                            authentication.getAuthorizedClientRegistrationId(),
                            authentication.getPrincipal(),
                            new UpdateChoreParticipationsCommand(
                                    request.addChoreIds(),
                                    request.removeChoreIds(),
                                    request.applicationScope(),
                                    request.expectedVersions()
                            ),
                            Instant.now()
                    );
                    return new CommandResponse<>(
                            200,
                            toResponse(updated),
                            null,
                            null
                    );
                }
        );
        return RotationHttpResponses.from(result);
    }

    private UpdateChoreParticipationsResponse toResponse(UpdatedChoreParticipations updated) {
        return new UpdateChoreParticipationsResponse(
                updated.membershipId(),
                updated.applicationScope(),
                updated.chores().stream()
                        .map(chore -> new UpdateChoreParticipationsResponse.ChoreChange(
                                chore.choreId(),
                                chore.action(),
                                chore.changed(),
                                chore.version(),
                                chore.rebuiltOccurrenceCount(),
                                chore.reassignedOccurrenceCount(),
                                chore.needsAttentionOccurrenceCount()
                        ))
                        .toList()
        );
    }
}
