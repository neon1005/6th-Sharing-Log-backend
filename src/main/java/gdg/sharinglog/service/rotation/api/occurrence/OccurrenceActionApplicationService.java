package gdg.sharinglog.service.rotation.api.occurrence;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import gdg.sharinglog.domain.GroupMember;
import gdg.sharinglog.domain.rotation.AssignmentTrigger;
import gdg.sharinglog.domain.rotation.Chore;
import gdg.sharinglog.domain.rotation.ChoreEligibilityMode;
import gdg.sharinglog.domain.rotation.ChoreOccurrence;
import gdg.sharinglog.domain.rotation.OccurrenceEligibleMember;
import gdg.sharinglog.domain.rotation.OccurrenceStatus;
import gdg.sharinglog.repository.GroupMemberRepository;
import gdg.sharinglog.repository.rotation.ChoreEligibleMemberRepository;
import gdg.sharinglog.repository.rotation.ChoreAssignmentAttemptRepository;
import gdg.sharinglog.repository.rotation.ChoreOccurrenceRepository;
import gdg.sharinglog.repository.rotation.ChoreRepository;
import gdg.sharinglog.repository.rotation.OccurrenceEligibleMemberRepository;
import gdg.sharinglog.service.rotation.assignment.RotationAssignmentService;
import gdg.sharinglog.service.rotation.occurrence.OccurrenceCommandService;
import gdg.sharinglog.service.rotation.occurrence.OccurrencePlanService;
import gdg.sharinglog.service.rotation.access.RotationActor;
import gdg.sharinglog.service.rotation.access.RotationActorAccessService;
import gdg.sharinglog.web.rotation.RotationViewMapper;
import gdg.sharinglog.web.rotation.dto.OccurrenceActionResponse;
import gdg.sharinglog.web.rotation.dto.RetryAssignmentRequest;
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
public class OccurrenceActionApplicationService {

    private final RotationActorAccessService accessService;
    private final ChoreOccurrenceRepository occurrenceRepository;
    private final ChoreRepository choreRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final ChoreEligibleMemberRepository choreEligibleMemberRepository;
    private final ChoreAssignmentAttemptRepository assignmentRepository;
    private final OccurrenceEligibleMemberRepository occurrenceEligibleMemberRepository;
    private final OccurrenceCommandService commandService;
    private final RotationAssignmentService assignmentService;
    private final OccurrencePlanService occurrencePlanService;
    private final RotationViewMapper viewMapper;

    @Transactional
    public OccurrenceActionResponse complete(
            String groupPublicId,
            String occurrencePublicId,
            String registrationId,
            OAuth2User principal,
            long expectedVersion,
            String note,
            Instant actedAt
    ) {
        Instant effectiveActedAt = Objects.requireNonNull(actedAt, "Action time is required.");
        ActionContext context = requireAssigneeAction(
                groupPublicId,
                occurrencePublicId,
                registrationId,
                principal,
                expectedVersion
        );
        requireStarted(context.occurrence(), effectiveActedAt);
        ChoreOccurrence occurrence = commandService.complete(
                occurrencePublicId,
                context.actor().membership().getPublicId(),
                effectiveActedAt,
                note
        );
        occurrencePlanService.regenerateFuture(occurrence.getChore(), effectiveActedAt);
        return response(OccurrenceActionResponse.Outcome.COMPLETED, occurrence, null, null);
    }

    @Transactional
    public OccurrenceActionResponse undoCompletion(
            String groupPublicId,
            String occurrencePublicId,
            String registrationId,
            OAuth2User principal,
            long expectedVersion,
            String note,
            Instant actedAt
    ) {
        RotationActor actor =
                accessService.requireActiveMemberForUpdate(groupPublicId, registrationId, principal);
        ChoreOccurrence occurrence = lockedOccurrence(groupPublicId, occurrencePublicId);
        requireVersion(occurrence, expectedVersion);
        requireStatus(occurrence, OccurrenceStatus.COMPLETED);
        boolean completedByActor = assignmentRepository
                .findFirstByOccurrence_IdOrderBySequenceNumberDesc(occurrence.getId())
                .filter(assignment -> assignment.isEffectiveCompletion()
                        && assignment.getAssignee().getId()
                        .equals(actor.membership().getId()))
                .isPresent();
        if (!completedByActor) {
            throw new RotationForbiddenException(
                    RotationProblemCode.FORBIDDEN,
                    "Only the member who completed this occurrence can undo it.",
                    Map.of("resourceId", occurrencePublicId)
            );
        }
        ChoreOccurrence reopened = commandService.undoCompletion(
                occurrencePublicId,
                actor.membership().getPublicId(),
                actedAt,
                note
        );
        occurrencePlanService.regenerateFuture(reopened.getChore(), actedAt);
        return response(
                OccurrenceActionResponse.Outcome.COMPLETION_UNDONE,
                reopened,
                null,
                null
        );
    }

    @Transactional
    public OccurrenceActionResponse retryAssignment(
            String groupPublicId,
            String occurrencePublicId,
            String registrationId,
            OAuth2User principal,
            long expectedVersion,
            RetryAssignmentRequest request,
            Instant actedAt
    ) {
        Objects.requireNonNull(request, "Retry request is required.");
        RotationActor actor =
                accessService.requireOwnerForUpdate(groupPublicId, registrationId, principal);
        ChoreOccurrence occurrence = lockedOccurrence(groupPublicId, occurrencePublicId);
        requireVersion(occurrence, expectedVersion);
        requireStatus(occurrence, OccurrenceStatus.NEEDS_ATTENTION);

        Long appliedChoreVersion = null;
        if (request.eligibilitySource()
                == RetryAssignmentRequest.EligibilitySource.CURRENT_CHORE) {
            Chore chore = choreRepository.findByIdForUpdate(occurrence.getChore().getId())
                    .orElseThrow(() -> new RotationNotFoundException(
                            "The chore for this occurrence was not found."
                    ));
            requireChoreVersion(chore, request.sourceChoreVersion());
            refreshEligibilitySnapshot(occurrence, chore, actedAt);
            appliedChoreVersion = chore.getVersion();
        }

        assignmentService.assign(
                occurrence.getId(),
                AssignmentTrigger.NEEDS_ATTENTION_RETRY,
                Objects.requireNonNull(actedAt, "Action time is required.")
        );
        occurrenceRepository.flush();
        regenerateDownstreamIfStarted(occurrence, actedAt);

        OccurrenceActionResponse.Outcome outcome =
                occurrence.getStatus() == OccurrenceStatus.ASSIGNED
                        ? OccurrenceActionResponse.Outcome.REASSIGNED
                        : OccurrenceActionResponse.Outcome.STILL_NEEDS_ATTENTION;
        return response(
                outcome,
                occurrence,
                occurrence.getEligibilitySnapshotVersion(),
                appliedChoreVersion
        );
    }

    private ActionContext requireAssigneeAction(
            String groupPublicId,
            String occurrencePublicId,
            String registrationId,
            OAuth2User principal,
            long expectedVersion
    ) {
        RotationActor actor =
                accessService.requireActiveMemberForUpdate(groupPublicId, registrationId, principal);
        ChoreOccurrence occurrence = lockedOccurrence(groupPublicId, occurrencePublicId);
        requireVersion(occurrence, expectedVersion);
        requireStatus(occurrence, OccurrenceStatus.ASSIGNED);
        boolean currentAssignee = occurrence.currentAssignee()
                .map(GroupMember::getId)
                .filter(actor.membership().getId()::equals)
                .isPresent();
        if (!currentAssignee) {
            throw new RotationForbiddenException(
                    RotationProblemCode.NOT_CURRENT_ASSIGNEE,
                    "Only the current assignee can perform this action.",
                    Map.of("resourceId", occurrencePublicId)
            );
        }
        return new ActionContext(actor, occurrence);
    }

    private ChoreOccurrence lockedOccurrence(String groupPublicId, String occurrencePublicId) {
        return occurrenceRepository.findByPublicIdAndGroupPublicIdForUpdate(
                        occurrencePublicId,
                        groupPublicId
                )
                .orElseThrow(() -> new RotationNotFoundException(
                        "The occurrence was not found in this group."
                ));
    }

    private void requireStarted(ChoreOccurrence occurrence, Instant actedAt) {
        var actedOn = actedAt
                .atZone(java.time.ZoneId.of(occurrence.getTimeZoneIdSnapshot()))
                .toLocalDate();
        if (actedOn.isBefore(occurrence.getPeriodStart())) {
            throw new RotationConflictException(
                    RotationProblemCode.INVALID_OCCURRENCE_STATE,
                    "A future occurrence cannot be completed.",
                    Map.of(
                            "resourceId", occurrence.getPublicId(),
                            "periodStart", occurrence.getPeriodStart()
                    )
            );
        }
    }

    private void regenerateDownstreamIfStarted(ChoreOccurrence occurrence, Instant changedAt) {
        var changedOn = changedAt
                .atZone(java.time.ZoneId.of(occurrence.getTimeZoneIdSnapshot()))
                .toLocalDate();
        if (!occurrence.getPeriodStart().isAfter(changedOn)) {
            occurrencePlanService.regenerateFuture(occurrence.getChore(), changedAt);
        }
    }

    private void requireVersion(ChoreOccurrence occurrence, long expectedVersion) {
        if (occurrence.getVersion() != expectedVersion) {
            throw new RotationConflictException(
                    RotationProblemCode.VERSION_CONFLICT,
                    "The occurrence changed. Reload it and try again.",
                    Map.of(
                            "resourceId", occurrence.getPublicId(),
                            "expectedVersion", expectedVersion,
                            "currentVersion", occurrence.getVersion(),
                            "currentStatus", occurrence.getStatus()
                    )
            );
        }
    }

    private void requireStatus(ChoreOccurrence occurrence, OccurrenceStatus expected) {
        if (occurrence.getStatus() != expected) {
            throw new RotationConflictException(
                    RotationProblemCode.INVALID_OCCURRENCE_STATE,
                    "This action is not allowed in the current occurrence state.",
                    Map.of(
                            "resourceId", occurrence.getPublicId(),
                            "currentVersion", occurrence.getVersion(),
                            "currentStatus", occurrence.getStatus()
                    )
            );
        }
    }

    private void requireChoreVersion(Chore chore, Long expectedVersion) {
        if (expectedVersion == null || chore.getVersion() != expectedVersion) {
            throw new RotationConflictException(
                    RotationProblemCode.CHORE_VERSION_CONFLICT,
                    "The chore eligibility settings changed. Reload them and try again.",
                    Map.of(
                            "resourceId", chore.getPublicId(),
                            "currentVersion", chore.getVersion()
                    )
            );
        }
    }

    private void refreshEligibilitySnapshot(
            ChoreOccurrence occurrence,
            Chore chore,
            Instant actedAt
    ) {
        occurrence.advanceEligibilitySnapshotVersion();
        occurrenceRepository.saveAndFlush(occurrence);
        List<GroupMember> eligibleMembers =
                chore.getEligibilityMode() == ChoreEligibilityMode.ALL_ACTIVE_MEMBERS
                        ? groupMemberRepository.findAllByGroup_IdAndStatusOrderById(
                                chore.getGroup().getId(),
                                gdg.sharinglog.domain.MemberStatus.ACTIVE
                        )
                        : choreEligibleMemberRepository.findAllByChore_IdOrderById(chore.getId())
                                .stream()
                                .map(eligible -> eligible.getMember())
                                .toList();
        occurrenceEligibleMemberRepository.saveAllAndFlush(
                eligibleMembers.stream()
                        .map(member -> new OccurrenceEligibleMember(
                                occurrence,
                                occurrence.getEligibilitySnapshotVersion(),
                                member,
                                actedAt
                        ))
                        .toList()
        );
    }

    private OccurrenceActionResponse response(
            OccurrenceActionResponse.Outcome outcome,
            ChoreOccurrence occurrence,
            Integer eligibilitySnapshotVersion,
            Long appliedChoreVersion
    ) {
        return new OccurrenceActionResponse(
                outcome,
                eligibilitySnapshotVersion,
                appliedChoreVersion,
                viewMapper.actionState(occurrence)
        );
    }

    private record ActionContext(
            RotationActor actor,
            ChoreOccurrence occurrence
    ) {
    }
}
