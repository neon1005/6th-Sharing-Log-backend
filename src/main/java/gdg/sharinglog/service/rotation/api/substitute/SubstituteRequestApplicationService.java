package gdg.sharinglog.service.rotation.api.substitute;

import static gdg.sharinglog.domain.rotation.AssignmentEndReason.SUBSTITUTE_REQUEST_RECIPIENT_EXCLUSIONS;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import gdg.sharinglog.domain.GroupMember;
import gdg.sharinglog.domain.rotation.AssignmentEndReason;
import gdg.sharinglog.domain.rotation.AssignmentTrigger;
import gdg.sharinglog.domain.rotation.ChoreAssignmentAttempt;
import gdg.sharinglog.domain.rotation.ChoreOccurrence;
import gdg.sharinglog.domain.rotation.OccurrenceStatus;
import gdg.sharinglog.domain.rotation.SubstituteRecipientStatus;
import gdg.sharinglog.domain.rotation.SubstituteRequest;
import gdg.sharinglog.domain.rotation.SubstituteRequestRecipient;
import gdg.sharinglog.domain.rotation.SubstituteRequestStatus;
import gdg.sharinglog.repository.rotation.ChoreAssignmentAttemptRepository;
import gdg.sharinglog.repository.rotation.ChoreOccurrenceRepository;
import gdg.sharinglog.repository.rotation.OccurrenceEligibleMemberRepository;
import gdg.sharinglog.repository.rotation.SubstituteRequestRecipientRepository;
import gdg.sharinglog.repository.rotation.SubstituteRequestRepository;
import gdg.sharinglog.service.push.PushNotifier;
import gdg.sharinglog.service.rotation.assignment.DirectAssignmentService;
import gdg.sharinglog.service.rotation.access.RotationActor;
import gdg.sharinglog.service.rotation.access.RotationActorAccessService;
import gdg.sharinglog.web.rotation.RotationViewMapper;
import gdg.sharinglog.web.rotation.dto.SubstituteRequestListResponse;
import gdg.sharinglog.web.rotation.dto.SubstituteRequestResponse;
import gdg.sharinglog.web.rotation.error.RotationConflictException;
import gdg.sharinglog.web.rotation.error.RotationForbiddenException;
import gdg.sharinglog.web.rotation.error.RotationNotFoundException;
import gdg.sharinglog.web.rotation.error.RotationProblemCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SubstituteRequestApplicationService {

    private final RotationActorAccessService accessService;
    private final ChoreOccurrenceRepository occurrenceRepository;
    private final ChoreAssignmentAttemptRepository assignmentRepository;
    private final OccurrenceEligibleMemberRepository eligibilityRepository;
    private final SubstituteRequestRepository requestRepository;
    private final SubstituteRequestRecipientRepository recipientRepository;
    private final DirectAssignmentService directAssignmentService;
    private final RotationViewMapper viewMapper;
    private final PushNotifier pushNotifier;

    @Transactional
    public SubstituteRequestResponse create(
            String groupPublicId,
            String occurrencePublicId,
            String registrationId,
            OAuth2User principal,
            long expectedOccurrenceVersion,
            String reason,
            Instant createdAt
    ) {
        RotationActor actor =
                accessService.requireActiveMemberForUpdate(groupPublicId, registrationId, principal);
        ChoreOccurrence occurrence = lockedOccurrence(groupPublicId, occurrencePublicId);
        requireVersion(occurrence.getPublicId(), expectedOccurrenceVersion, occurrence.getVersion());
        var activeRequest = requestRepository
                .findByOccurrence_IdAndActiveMarker(occurrence.getId(), 1);
        if (activeRequest.isPresent()) {
            if (!activeRequest.get().requester().getId()
                    .equals(actor.membership().getId())) {
                throw new RotationForbiddenException(
                        RotationProblemCode.SUBSTITUTE_REQUESTED_BY_ANOTHER_MEMBER,
                        "다른 사용자가 올린 대타 요청입니다",
                        Map.of("resourceId", occurrencePublicId)
                );
            }
            throw conflict(
                    RotationProblemCode.SUBSTITUTE_REQUEST_ALREADY_EXISTS,
                    "An active substitute request already exists for this assignment.",
                    occurrencePublicId
            );
        }
        requireAssignedToActor(occurrence, actor);

        List<GroupMember> recipients = eligibilityRepository
                .findAllByOccurrence_IdAndSnapshotVersionOrderById(
                        occurrence.getId(),
                        occurrence.getEligibilitySnapshotVersion()
                )
                .stream()
                .filter(snapshot -> snapshot.belongsToCurrentActivation())
                .map(snapshot -> snapshot.getMember())
                .filter(GroupMember::isActive)
                .filter(member -> !member.getId().equals(actor.membership().getId()))
                .filter(member -> !assignmentRepository
                        .existsByOccurrence_IdAndAssignee_IdAndEndReasonIn(
                                occurrence.getId(),
                                member.getId(),
                                SUBSTITUTE_REQUEST_RECIPIENT_EXCLUSIONS
                        ))
                .toList();
        if (recipients.isEmpty()) {
            throw conflict(
                    RotationProblemCode.NO_SUBSTITUTE_CANDIDATE,
                    "No active eligible substitute recipient is available.",
                    occurrencePublicId
            );
        }

        Instant effectiveCreatedAt =
                Objects.requireNonNull(createdAt, "대타 요청 시각은 필수입니다.");
        SubstituteRequest request = requestRepository.saveAndFlush(
                SubstituteRequest.pending(
                        occurrence,
                        occurrence.getCurrentAssignment(),
                        reason,
                        effectiveCreatedAt
                )
        );
        recipientRepository.saveAllAndFlush(
                recipients.stream()
                        .map(member -> new SubstituteRequestRecipient(request, member))
                        .toList()
        );
        notifyRecipientsOfNewRequest(request, actor, recipients);
        return response(request, actor);
    }

    private void notifyRecipientsOfNewRequest(
            SubstituteRequest request,
            RotationActor actor,
            List<GroupMember> recipients
    ) {
        String requesterName = viewMapper.member(actor.membership()).displayName();
        String choreName = request.getOccurrence().getChoreNameSnapshot();
        String body = requesterName + "님이 '" + choreName + "' 대타를 요청했어요";
        for (GroupMember recipient : recipients) {
            pushNotifier.notifyUser(recipient.getUser().getId(), "대타 요청", body, "/notification");
        }
    }

    @Transactional(readOnly = true)
    public SubstituteRequestListResponse findAll(
            String groupPublicId,
            String registrationId,
            OAuth2User principal,
            SubstituteRequestBox box,
            SubstituteRequestStatus status
    ) {
        RotationActor actor =
                accessService.requireActiveMember(groupPublicId, registrationId, principal);
        SubstituteRequestBox effectiveBox =
                box == null ? SubstituteRequestBox.INBOX : box;
        List<SubstituteRequestResponse> items = requestRepository
                .findAllByOccurrence_Chore_Group_IdOrderByCreatedAtDescIdDesc(
                        actor.group().getId()
                )
                .stream()
                .filter(request -> status == null || request.getStatus() == status)
                .filter(request -> visibleInBox(request, actor, effectiveBox))
                .map(request -> response(request, actor))
                .toList();
        return new SubstituteRequestListResponse(
                actor.group().getPublicId(),
                effectiveBox,
                items,
                items.size()
        );
    }

    @Transactional(readOnly = true)
    public SubstituteRequestResponse findOne(
            String groupPublicId,
            String requestPublicId,
            String registrationId,
            OAuth2User principal
    ) {
        RotationActor actor =
                accessService.requireActiveMember(groupPublicId, registrationId, principal);
        SubstituteRequest request = requestRepository
                .findByPublicIdAndOccurrence_Chore_Group_PublicId(
                        requestPublicId,
                        groupPublicId
                )
                .orElseThrow(() -> new RotationNotFoundException(
                        "The substitute request was not found in this group."
                ));
        if (!visibleInBox(request, actor, SubstituteRequestBox.ALL)) {
            throw new RotationForbiddenException(
                    RotationProblemCode.FORBIDDEN,
                    "Only the requester, a recipient, or the group owner can view this request.",
                    Map.of("resourceId", requestPublicId)
            );
        }
        return response(request, actor);
    }

    @Transactional
    public SubstituteRequestResponse accept(
            String groupPublicId,
            String requestPublicId,
            String registrationId,
            OAuth2User principal,
            long expectedRequestVersion,
            Instant acceptedAt
    ) {
        RotationActor actor =
                accessService.requireActiveMemberForUpdate(groupPublicId, registrationId, principal);
        ChoreOccurrence occurrence = lockRequestOccurrence(groupPublicId, requestPublicId);
        SubstituteRequest request = lockedRequest(groupPublicId, requestPublicId);
        requireVersion(request.getPublicId(), expectedRequestVersion, request.getVersion());
        requireLiveRequest(request, occurrence);
        SubstituteRequestRecipient recipient = requirePendingRecipient(request, actor);

        Instant effectiveAcceptedAt =
                Objects.requireNonNull(acceptedAt, "대타 수락 시각은 필수입니다.");
        occurrence.releaseForReassignment(
                AssignmentEndReason.SUBSTITUTE_ACCEPTED,
                effectiveAcceptedAt,
                "Substitute request " + request.getPublicId() + " accepted"
        );
        occurrenceRepository.saveAndFlush(occurrence);
        ChoreAssignmentAttempt acceptedAssignment = directAssignmentService.assign(
                occurrence,
                actor.membership(),
                AssignmentTrigger.SUBSTITUTE_ACCEPTANCE,
                effectiveAcceptedAt,
                "VOLUNTEER_ACCEPTED_SUBSTITUTE_REQUEST"
        );

        recipient.accept(effectiveAcceptedAt);
        List<SubstituteRequestRecipient> allRecipients =
                recipientRepository.findAllByRequest_IdOrderById(request.getId());
        allRecipients.stream()
                .filter(item -> !item.getId().equals(recipient.getId()))
                .forEach(item -> item.markIneligible(effectiveAcceptedAt));
        recipientRepository.saveAll(allRecipients);
        request.accept(acceptedAssignment, effectiveAcceptedAt);
        requestRepository.saveAndFlush(request);
        return response(request, actor);
    }

    @Transactional
    public SubstituteRequestResponse decline(
            String groupPublicId,
            String requestPublicId,
            String registrationId,
            OAuth2User principal,
            long expectedRequestVersion,
            Instant declinedAt
    ) {
        RotationActor actor =
                accessService.requireActiveMemberForUpdate(groupPublicId, registrationId, principal);
        ChoreOccurrence occurrence = lockRequestOccurrence(groupPublicId, requestPublicId);
        SubstituteRequest request = lockedRequest(groupPublicId, requestPublicId);
        requireVersion(request.getPublicId(), expectedRequestVersion, request.getVersion());
        requireLiveRequest(request, occurrence);
        SubstituteRequestRecipient recipient = requirePendingRecipient(request, actor);

        Instant effectiveDeclinedAt =
                Objects.requireNonNull(declinedAt, "대타 거절 시각은 필수입니다.");
        recipient.decline(effectiveDeclinedAt);
        recipientRepository.saveAndFlush(recipient);
        request.recordResponse(effectiveDeclinedAt);
        boolean hasPending = recipientRepository
                .findAllByRequest_IdOrderById(request.getId())
                .stream()
                .anyMatch(item -> item.getResponseStatus().isPending());
        if (!hasPending) {
            request.exhaust(effectiveDeclinedAt);
        }
        requestRepository.saveAndFlush(request);
        return response(request, actor);
    }

    private ChoreOccurrence lockRequestOccurrence(
            String groupPublicId,
            String requestPublicId
    ) {
        String occurrencePublicId = requestRepository
                .findOccurrencePublicId(requestPublicId, groupPublicId)
                .orElseThrow(() -> new RotationNotFoundException(
                        "The substitute request was not found in this group."
                ));
        return lockedOccurrence(groupPublicId, occurrencePublicId);
    }

    private SubstituteRequest lockedRequest(
            String groupPublicId,
            String requestPublicId
    ) {
        return requestRepository
                .findByPublicIdAndGroupPublicIdForUpdate(requestPublicId, groupPublicId)
                .orElseThrow(() -> new RotationNotFoundException(
                        "The substitute request was not found in this group."
                ));
    }

    private ChoreOccurrence lockedOccurrence(
            String groupPublicId,
            String occurrencePublicId
    ) {
        return occurrenceRepository
                .findByPublicIdAndGroupPublicIdForUpdate(
                        occurrencePublicId,
                        groupPublicId
                )
                .orElseThrow(() -> new RotationNotFoundException(
                        "The occurrence was not found in this group."
                ));
    }

    private void requireAssignedToActor(ChoreOccurrence occurrence, RotationActor actor) {
        boolean currentAssignee = occurrence.getStatus() == OccurrenceStatus.ASSIGNED
                && occurrence.currentAssignee()
                .map(GroupMember::getId)
                .filter(actor.membership().getId()::equals)
                .isPresent();
        if (!currentAssignee) {
            throw new RotationForbiddenException(
                    RotationProblemCode.NOT_CURRENT_ASSIGNEE,
                    "Only the current assignee can request a substitute.",
                    Map.of("resourceId", occurrence.getPublicId())
            );
        }
    }

    private void requireLiveRequest(
            SubstituteRequest request,
            ChoreOccurrence occurrence
    ) {
        boolean live = request.getStatus().isPending()
                && occurrence.getStatus() == OccurrenceStatus.ASSIGNED
                && occurrence.getCurrentAssignment() != null
                && occurrence.getCurrentAssignment().getId()
                .equals(request.getRequesterAssignment().getId());
        if (!live) {
            throw conflict(
                    RotationProblemCode.INVALID_SUBSTITUTE_REQUEST_STATE,
                    "The substitute request no longer matches the active assignment.",
                    request.getPublicId()
            );
        }
    }

    private void requireVersion(
            String resourceId,
            long expectedVersion,
            long currentVersion
    ) {
        if (currentVersion != expectedVersion) {
            throw versionConflict(resourceId, expectedVersion, currentVersion);
        }
    }

    private RotationConflictException versionConflict(
            String resourceId,
            long expectedVersion,
            long currentVersion
    ) {
        return new RotationConflictException(
                RotationProblemCode.VERSION_CONFLICT,
                "The resource changed. Reload it and try again.",
                Map.of(
                        "resourceId", resourceId,
                        "expectedVersion", expectedVersion,
                        "currentVersion", currentVersion
                )
        );
    }

    private RotationConflictException conflict(
            RotationProblemCode code,
            String detail,
            String resourceId
    ) {
        return new RotationConflictException(
                code,
                detail,
                Map.of("resourceId", resourceId)
        );
    }

    private SubstituteRequestRecipient requirePendingRecipient(
            SubstituteRequest request,
            RotationActor actor
    ) {
        SubstituteRequestRecipient recipient = recipientRepository
                .findForUpdate(request.getId(), actor.membership().getId())
                .orElseThrow(() -> new RotationForbiddenException(
                        RotationProblemCode.NOT_SUBSTITUTE_RECIPIENT,
                        "Only a recipient of this substitute request can respond.",
                        Map.of("resourceId", request.getPublicId())
                ));
        if (!recipient.getResponseStatus().isPending()
                || !recipient.belongsToCurrentActivation()) {
            throw conflict(
                    RotationProblemCode.INVALID_SUBSTITUTE_REQUEST_STATE,
                    "This member has already responded or is no longer eligible.",
                    request.getPublicId()
            );
        }
        return recipient;
    }

    private boolean visibleInBox(
            SubstituteRequest request,
            RotationActor actor,
            SubstituteRequestBox box
    ) {
        boolean requester =
                request.requester().getId().equals(actor.membership().getId());
        var recipient = recipientRepository.findAllByRequest_IdOrderById(request.getId())
                .stream()
                .filter(item -> item.getMember().getId().equals(actor.membership().getId()))
                .findFirst();
        return switch (box) {
            case INBOX -> request.getStatus().isPending()
                    && recipient.filter(item -> item.getResponseStatus().isPending()).isPresent();
            case OUTBOX -> requester;
            case ALL -> actor.isOwner() || requester || recipient.isPresent();
        };
    }

    private SubstituteRequestResponse response(
            SubstituteRequest request,
            RotationActor actor
    ) {
        var recipients = recipientRepository
                .findAllByRequest_IdOrderById(request.getId())
                .stream()
                .map(recipient -> new SubstituteRequestResponse.RecipientResponse(
                        viewMapper.member(recipient.getMember()),
                        recipient.getResponseStatus(),
                        recipient.getRespondedAt()
                ))
                .toList();
        return new SubstituteRequestResponse(
                request.getPublicId(),
                request.getStatus(),
                request.getReason(),
                viewMapper.member(request.requester()),
                request.getAcceptedAssignment() == null
                        ? null
                        : viewMapper.member(request.getAcceptedAssignment().getAssignee()),
                request.getOccurrence().getChore().getPublicId(),
                request.getOccurrence().getChoreNameSnapshot(),
                request.getOccurrence().getPeriodStart(),
                request.getOccurrence().getPeriodEndExclusive(),
                request.getOccurrence().getDueAt(),
                viewMapper.actionState(request.getOccurrence()),
                recipients,
                request.getCreatedAt(),
                request.getLastResponseAt(),
                request.getResolvedAt(),
                request.getVersion()
        );
    }
}
