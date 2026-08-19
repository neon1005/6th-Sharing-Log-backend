package gdg.sharinglog.service.rotation.api.member;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import gdg.sharinglog.domain.GroupMember;
import gdg.sharinglog.domain.rotation.AssignmentEndReason;
import gdg.sharinglog.domain.rotation.AssignmentTrigger;
import gdg.sharinglog.domain.rotation.Chore;
import gdg.sharinglog.domain.rotation.ChoreOccurrence;
import gdg.sharinglog.domain.rotation.OccurrenceEligibleMember;
import gdg.sharinglog.domain.rotation.OccurrenceStatus;
import gdg.sharinglog.repository.rotation.ChoreOccurrenceRepository;
import gdg.sharinglog.repository.rotation.ChoreRepository;
import gdg.sharinglog.repository.rotation.OccurrenceEligibleMemberRepository;
import gdg.sharinglog.service.rotation.assignment.RotationAssignmentService;
import gdg.sharinglog.service.rotation.enrollment.ChoreEnrollmentService;
import gdg.sharinglog.service.rotation.occurrence.OccurrencePlanService;
import gdg.sharinglog.service.rotation.access.RotationActor;
import gdg.sharinglog.service.rotation.access.RotationActorAccessService;
import gdg.sharinglog.service.rotation.substitute.SubstituteRequestLifecycleService;
import gdg.sharinglog.web.rotation.error.RotationConflictException;
import gdg.sharinglog.web.rotation.error.RotationNotFoundException;
import gdg.sharinglog.web.rotation.error.RotationProblemCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChoreParticipationApplicationService {

    private final RotationActorAccessService accessService;
    private final ChoreRepository choreRepository;
    private final ChoreOccurrenceRepository occurrenceRepository;
    private final OccurrenceEligibleMemberRepository eligibilityRepository;
    private final ChoreEnrollmentService enrollmentService;
    private final RotationAssignmentService assignmentService;
    private final SubstituteRequestLifecycleService substituteRequestLifecycleService;
    private final OccurrencePlanService occurrencePlanService;

    @Transactional
    public UpdatedChoreParticipations update(
            String groupPublicId,
            String membershipPublicId,
            String registrationId,
            OAuth2User principal,
            UpdateChoreParticipationsCommand command,
            Instant changedAt
    ) {
        UpdateChoreParticipationsCommand requiredCommand =
                Objects.requireNonNull(command, "참여 업무 변경 명령은 필수입니다.");
        Instant effectiveChangedAt =
                Objects.requireNonNull(changedAt, "참여 업무 변경 시각은 필수입니다.");
        RotationActor actor =
                accessService.requireOwnerForUpdate(groupPublicId, registrationId, principal);
        GroupMember target =
                accessService.requireActiveTargetMemberForUpdate(actor, membershipPublicId);

        Map<String, UpdatedChoreParticipations.Action> requestedActions =
                requestedActions(requiredCommand);
        List<LockedChange> lockedChanges =
                lockChanges(actor, requestedActions, requiredCommand.expectedVersions());
        verifyAllVersions(lockedChanges);

        List<PendingResult> pendingResults = new ArrayList<>(lockedChanges.size());
        for (LockedChange change : lockedChanges) {
            boolean changed;
            OccurrenceImpact impact = OccurrenceImpact.NONE;
            if (change.action() == UpdatedChoreParticipations.Action.ADD) {
                changed = enrollmentService.addOrReactivate(
                        change.chore(),
                        target,
                        effectiveChangedAt
                );
            } else {
                changed = enrollmentService.removeOrDisable(
                        change.chore(),
                        target,
                        effectiveChangedAt
                );
            }
            if (changed) {
                if (requiredCommand.applicationScope()
                        == ChoreParticipationApplicationScope.CURRENT_AND_FUTURE) {
                    List<GroupMember> activeAfter =
                            enrollmentService.findActiveMembers(change.chore());
                    impact = applyToOpenOccurrences(
                            change.chore(),
                            target,
                            change.action(),
                            activeAfter,
                            effectiveChangedAt
                    );
                }
            }
            pendingResults.add(new PendingResult(change, changed, impact));
        }

        List<Chore> changedChores = pendingResults.stream()
                .filter(PendingResult::changed)
                .map(result -> result.change().chore())
                .toList();
        if (!changedChores.isEmpty()) {
            occurrencePlanService.regenerateFutures(changedChores, effectiveChangedAt);
        }

        choreRepository.flush();
        List<UpdatedChoreParticipations.ChoreChange> results = pendingResults.stream()
                .map(PendingResult::toResult)
                .toList();
        return new UpdatedChoreParticipations(
                target.getPublicId(),
                requiredCommand.applicationScope(),
                results
        );
    }

    private Map<String, UpdatedChoreParticipations.Action> requestedActions(
            UpdateChoreParticipationsCommand command
    ) {
        Map<String, UpdatedChoreParticipations.Action> actions = new HashMap<>();
        command.addChoreIds().forEach(id ->
                actions.put(id, UpdatedChoreParticipations.Action.ADD));
        command.removeChoreIds().forEach(id ->
                actions.put(id, UpdatedChoreParticipations.Action.REMOVE));
        return actions;
    }

    private List<LockedChange> lockChanges(
            RotationActor actor,
            Map<String, UpdatedChoreParticipations.Action> requestedActions,
            Map<String, Long> expectedVersions
    ) {
        Map<String, Chore> choresByPublicId = new HashMap<>();
        choreRepository.findAllByGroup_IdOrderById(actor.group().getId())
                .forEach(chore -> choresByPublicId.put(chore.getPublicId(), chore));

        Set<String> missingIds = new HashSet<>(requestedActions.keySet());
        missingIds.removeAll(choresByPublicId.keySet());
        if (!missingIds.isEmpty()) {
            String missingId = missingIds.stream().sorted().findFirst().orElseThrow();
            throw new RotationNotFoundException(
                    "The chore was not found in this group.",
                    Map.of("resourceId", missingId)
            );
        }

        return requestedActions.entrySet().stream()
                .map(entry -> new RequestedChange(
                        choresByPublicId.get(entry.getKey()).getId(),
                        entry.getKey(),
                        entry.getValue(),
                        expectedVersions.get(entry.getKey())
                ))
                .sorted(Comparator.comparing(RequestedChange::internalId))
                .map(requested -> new LockedChange(
                        choreRepository.findByIdForUpdate(requested.internalId())
                                .orElseThrow(() -> new RotationNotFoundException(
                                        "The chore was not found in this group.",
                                        Map.of("resourceId", requested.publicId())
                                )),
                        requested.action(),
                        requested.expectedVersion()
                ))
                .toList();
    }

    private void verifyAllVersions(List<LockedChange> changes) {
        for (LockedChange change : changes) {
            if (change.chore().getVersion() != change.expectedVersion()) {
                throw new RotationConflictException(
                        RotationProblemCode.CHORE_VERSION_CONFLICT,
                        "One of the chores changed. Reload the participation settings and try again.",
                        Map.of(
                                "resourceId", change.chore().getPublicId(),
                                "expectedVersion", change.expectedVersion(),
                                "currentVersion", change.chore().getVersion()
                        )
                );
            }
        }
    }

    private OccurrenceImpact applyToOpenOccurrences(
            Chore chore,
            GroupMember target,
            UpdatedChoreParticipations.Action action,
            List<GroupMember> activeMembers,
            Instant changedAt
    ) {
        List<GroupMember> snapshotMembers = distinctActiveMembers(activeMembers);
        var activeOn = changedAt.atZone(chore.getGroup().timeZone()).toLocalDate();
        List<ChoreOccurrence> occurrences =
                occurrenceRepository.findAllOpenActiveOnByChoreIdForUpdate(
                        chore.getId(),
                        activeOn
                );
        int reassigned = 0;
        int needsAttention = 0;

        for (ChoreOccurrence occurrence : occurrences) {
            boolean assignedTargetRemoved =
                    action == UpdatedChoreParticipations.Action.REMOVE
                            && occurrence.currentAssignee()
                            .map(GroupMember::getId)
                            .filter(target.getId()::equals)
                            .isPresent();
            boolean retryAfterAddition =
                    action == UpdatedChoreParticipations.Action.ADD
                            && occurrence.getStatus() == OccurrenceStatus.NEEDS_ATTENTION;

            if (assignedTargetRemoved) {
                substituteRequestLifecycleService.cancelPendingForOccurrence(
                        occurrence,
                        changedAt
                );
                occurrence.releaseForReassignment(
                        AssignmentEndReason.PARTICIPATION_REMOVED,
                        changedAt
                );
            } else if (action == UpdatedChoreParticipations.Action.REMOVE) {
                substituteRequestLifecycleService.invalidatePendingForOccurrenceAndMember(
                        occurrence,
                        target,
                        changedAt
                );
            }
            occurrence.advanceEligibilitySnapshotVersion();
            occurrenceRepository.saveAndFlush(occurrence);
            eligibilityRepository.saveAllAndFlush(snapshotMembers.stream()
                    .map(member -> new OccurrenceEligibleMember(
                            occurrence,
                            occurrence.getEligibilitySnapshotVersion(),
                            member,
                            changedAt
                    ))
                    .toList());

            if (assignedTargetRemoved) {
                assignmentService.assign(
                        occurrence.getId(),
                        AssignmentTrigger.PARTICIPATION_CHANGE_REASSIGNMENT,
                        changedAt
                );
            } else if (retryAfterAddition) {
                assignmentService.assign(
                        occurrence.getId(),
                        AssignmentTrigger.PARTICIPATION_CHANGE_RETRY,
                        changedAt
                );
            }

            if ((assignedTargetRemoved || retryAfterAddition)
                    && occurrence.getStatus() == OccurrenceStatus.ASSIGNED) {
                reassigned++;
            }
            if (occurrence.getStatus() == OccurrenceStatus.NEEDS_ATTENTION) {
                needsAttention++;
            }
        }
        return new OccurrenceImpact(occurrences.size(), reassigned, needsAttention);
    }

    private List<GroupMember> distinctActiveMembers(List<GroupMember> members) {
        Set<Long> membershipIds = new HashSet<>();
        return members.stream()
                .filter(GroupMember::isActive)
                .filter(member -> membershipIds.add(member.getId()))
                .toList();
    }

    private record RequestedChange(
            Long internalId,
            String publicId,
            UpdatedChoreParticipations.Action action,
            long expectedVersion
    ) {
    }

    private record LockedChange(
            Chore chore,
            UpdatedChoreParticipations.Action action,
            long expectedVersion
    ) {
    }

    private record PendingResult(
            LockedChange change,
            boolean changed,
            OccurrenceImpact impact
    ) {

        private UpdatedChoreParticipations.ChoreChange toResult() {
            return new UpdatedChoreParticipations.ChoreChange(
                    change.chore().getPublicId(),
                    change.action(),
                    changed,
                    change.chore().getVersion(),
                    impact.rebuilt(),
                    impact.reassigned(),
                    impact.needsAttention()
            );
        }
    }

    private record OccurrenceImpact(
            int rebuilt,
            int reassigned,
            int needsAttention
    ) {

        private static final OccurrenceImpact NONE = new OccurrenceImpact(0, 0, 0);
    }
}
