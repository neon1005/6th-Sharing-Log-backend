package gdg.sharinglog.service.rotation.occurrence;

import static gdg.sharinglog.domain.rotation.AssignmentEndReason.SAME_OCCURRENCE_EXCLUSIONS;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import gdg.sharinglog.domain.GroupMember;
import gdg.sharinglog.domain.GroupRole;
import gdg.sharinglog.domain.MemberStatus;
import gdg.sharinglog.domain.rotation.AssignmentEndReason;
import gdg.sharinglog.domain.rotation.AssignmentTrigger;
import gdg.sharinglog.domain.rotation.ChoreOccurrence;
import gdg.sharinglog.domain.rotation.OccurrenceStatus;
import gdg.sharinglog.repository.GroupMemberRepository;
import gdg.sharinglog.repository.SharingGroupRepository;
import gdg.sharinglog.repository.rotation.ChoreAssignmentAttemptRepository;
import gdg.sharinglog.repository.rotation.ChoreOccurrenceRepository;
import gdg.sharinglog.repository.rotation.OccurrenceEligibleMemberRepository;
import gdg.sharinglog.service.rotation.assignment.DirectAssignmentService;
import gdg.sharinglog.service.rotation.assignment.RotationAssignmentService;
import gdg.sharinglog.service.rotation.exception.LastOwnerCannotLeaveException;
import gdg.sharinglog.service.rotation.exception.MemberNotFoundException;
import gdg.sharinglog.service.rotation.exception.OccurrenceCommandConflictException;
import gdg.sharinglog.service.rotation.exception.OccurrenceNotFoundException;
import gdg.sharinglog.service.rotation.substitute.SubstituteRequestLifecycleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OccurrenceCommandService {

    private final SharingGroupRepository sharingGroupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final ChoreOccurrenceRepository occurrenceRepository;
    private final ChoreAssignmentAttemptRepository assignmentRepository;
    private final OccurrenceEligibleMemberRepository eligibilityRepository;
    private final RotationAssignmentService assignmentService;
    private final DirectAssignmentService directAssignmentService;
    private final SubstituteRequestLifecycleService substituteRequestLifecycleService;

    @Transactional
    public ChoreOccurrence complete(
            String occurrencePublicId,
            String actorMemberPublicId,
            Instant completedAt
    ) {
        return complete(occurrencePublicId, actorMemberPublicId, completedAt, null);
    }

    @Transactional
    public ChoreOccurrence complete(
            String occurrencePublicId,
            String actorMemberPublicId,
            Instant completedAt,
            String actorNote
    ) {
        Instant effectiveCompletedAt = Objects.requireNonNull(
                completedAt,
                "Completion time is required."
        );
        ChoreOccurrence occurrence = lockedOccurrence(occurrencePublicId);
        GroupMember actor = requireActorOfOccurrence(actorMemberPublicId, occurrence);
        if (occurrence.getStatus() == OccurrenceStatus.COMPLETED) {
            requireTerminalActor(occurrence, actor, AssignmentEndReason.COMPLETED);
            return occurrence;
        }
        requireCurrentAssignee(occurrence, actor);
        var completedOn = effectiveCompletedAt
                .atZone(java.time.ZoneId.of(occurrence.getTimeZoneIdSnapshot()))
                .toLocalDate();
        if (completedOn.isBefore(occurrence.getPeriodStart())) {
            throw new OccurrenceCommandConflictException(
                    "A future occurrence cannot be completed."
            );
        }
        substituteRequestLifecycleService.cancelPendingForOccurrence(
                occurrence,
                effectiveCompletedAt
        );
        occurrence.complete(
                effectiveCompletedAt,
                actorNote
        );
        return occurrenceRepository.saveAndFlush(occurrence);
    }

    @Transactional
    public ChoreOccurrence skipAlreadyDone(
            String occurrencePublicId,
            String actorMemberPublicId,
            Instant skippedAt
    ) {
        return skipAlreadyDone(occurrencePublicId, actorMemberPublicId, skippedAt, null);
    }

    @Transactional
    public ChoreOccurrence skipAlreadyDone(
            String occurrencePublicId,
            String actorMemberPublicId,
            Instant skippedAt,
            String actorNote
    ) {
        ChoreOccurrence occurrence = lockedOccurrence(occurrencePublicId);
        GroupMember actor = requireActorOfOccurrence(actorMemberPublicId, occurrence);
        if (occurrence.getStatus() == OccurrenceStatus.SKIPPED) {
            requireTerminalActor(
                    occurrence,
                    actor,
                    AssignmentEndReason.SKIPPED_ALREADY_DONE
            );
            return occurrence;
        }
        requireCurrentAssignee(occurrence, actor);
        substituteRequestLifecycleService.cancelPendingForOccurrence(
                occurrence,
                skippedAt
        );
        occurrence.skipAlreadyDone(
                Objects.requireNonNull(skippedAt, "생략 시각은 필수입니다."),
                actorNote
        );
        return occurrenceRepository.saveAndFlush(occurrence);
    }

    @Transactional
    public ChoreOccurrence undoCompletion(
            String occurrencePublicId,
            String actorMemberPublicId,
            Instant undoneAt,
            String actorNote
    ) {
        ChoreOccurrence occurrence = lockedOccurrence(occurrencePublicId);
        GroupMember actor = requireActorOfOccurrence(actorMemberPublicId, occurrence);
        if (occurrence.getStatus() != OccurrenceStatus.COMPLETED) {
            throw new OccurrenceCommandConflictException(
                    "완료된 회차만 완료 취소할 수 있습니다."
            );
        }
        var completedAssignment = assignmentRepository
                .findFirstByOccurrence_IdOrderBySequenceNumberDesc(occurrence.getId())
                .orElseThrow(() -> new IllegalStateException("완료 회차에 배정 이력이 없습니다."));
        if (!completedAssignment.getAssignee().getId().equals(actor.getId())
                || !completedAssignment.isEffectiveCompletion()) {
            throw new OccurrenceCommandConflictException(
                    "마지막으로 완료 처리한 담당자만 완료 취소할 수 있습니다."
            );
        }
        Instant effectiveUndoneAt =
                Objects.requireNonNull(undoneAt, "완료 취소 시각은 필수입니다.");
        completedAssignment.revokeCompletion(actor, effectiveUndoneAt, actorNote);
        assignmentRepository.saveAndFlush(completedAssignment);
        directAssignmentService.reopenCompleted(
                occurrence,
                actor,
                effectiveUndoneAt,
                "COMPLETION_UNDONE_BY_LAST_ASSIGNEE"
        );
        occurrenceRepository.flush();
        return occurrence;
    }

    @Transactional
    public ChoreOccurrence declineCurrentOccurrence(
            String occurrencePublicId,
            String actorMemberPublicId,
            Instant declinedAt
    ) {
        return declineCurrentOccurrence(
                occurrencePublicId,
                actorMemberPublicId,
                declinedAt,
                null
        );
    }

    @Transactional
    public ChoreOccurrence declineCurrentOccurrence(
            String occurrencePublicId,
            String actorMemberPublicId,
            Instant declinedAt,
            String actorNote
    ) {
        ChoreOccurrence occurrence = lockedOccurrence(occurrencePublicId);
        GroupMember actor = requireActorOfOccurrence(actorMemberPublicId, occurrence);
        if (assignmentRepository.existsByOccurrence_IdAndAssignee_IdAndEndReason(
                occurrence.getId(),
                actor.getId(),
                AssignmentEndReason.DECLINED_BY_ASSIGNEE
        )) {
            return occurrence;
        }

        requireCurrentAssignee(occurrence, actor);
        Instant effectiveDeclinedAt = Objects.requireNonNull(declinedAt, "수행 불가 시각은 필수입니다.");
        substituteRequestLifecycleService.cancelPendingForOccurrence(
                occurrence,
                effectiveDeclinedAt
        );
        occurrence.releaseForReassignment(
                AssignmentEndReason.DECLINED_BY_ASSIGNEE,
                effectiveDeclinedAt,
                actorNote
        );
        occurrenceRepository.saveAndFlush(occurrence);
        assignmentService.assign(
                occurrence.getId(),
                AssignmentTrigger.DECLINE_REASSIGNMENT,
                effectiveDeclinedAt
        );
        occurrenceRepository.flush();
        return occurrence;
    }

    @Transactional
    public List<ChoreOccurrence> leaveMember(
            String memberPublicId,
            Instant leftAt
    ) {
        Instant effectiveLeftAt = Objects.requireNonNull(
                leftAt,
                "Member leave time is required."
        );
        String requiredMemberPublicId =
                Objects.requireNonNull(memberPublicId, "멤버 공개 ID는 필수입니다.");
        Long groupId = groupMemberRepository.findGroupIdByPublicId(requiredMemberPublicId)
                .orElseThrow(() -> new MemberNotFoundException(requiredMemberPublicId));
        var group = sharingGroupRepository.findByIdForUpdate(groupId)
                .orElseThrow(() -> new IllegalStateException("멤버의 그룹을 찾을 수 없습니다."));
        GroupMember member = groupMemberRepository.findByPublicIdForUpdate(memberPublicId)
                .orElseThrow(() -> new MemberNotFoundException(memberPublicId));
        if (!member.isActive()) {
            return List.of();
        }
        if (member.getRole() == GroupRole.OWNER
                && groupMemberRepository.countByGroup_IdAndStatusAndRole(
                        groupId,
                        MemberStatus.ACTIVE,
                        GroupRole.OWNER
                ) <= 1
                && groupMemberRepository.existsByGroup_IdAndStatusAndIdNot(
                        groupId,
                        MemberStatus.ACTIVE,
                        member.getId()
                )) {
            throw new LastOwnerCannotLeaveException();
        }

        List<ChoreOccurrence> lockedAffected = occurrenceRepository
                .findAllCurrentOrPastAssignedToMemberForUpdate(
                        member.getId(),
                        effectiveLeftAt.atZone(group.timeZone()).toLocalDate()
                );

        substituteRequestLifecycleService.invalidatePendingForMember(
                member,
                effectiveLeftAt
        );
        member.leave(effectiveLeftAt);
        groupMemberRepository.saveAndFlush(member);

        List<ChoreOccurrence> affected = lockedAffected.stream()
                .sorted(Comparator
                        .comparingLong(this::eligibleCandidateCount)
                        .thenComparing(ChoreOccurrence::getId))
                .toList();

        for (ChoreOccurrence occurrence : affected) {
            substituteRequestLifecycleService.cancelPendingForOccurrence(
                    occurrence,
                    effectiveLeftAt
            );
            occurrence.releaseForReassignment(
                    AssignmentEndReason.ASSIGNEE_LEFT_GROUP,
                    effectiveLeftAt
            );
            occurrenceRepository.saveAndFlush(occurrence);
            assignmentService.assign(
                    occurrence.getId(),
                    AssignmentTrigger.MEMBER_LEFT_REASSIGNMENT,
                    effectiveLeftAt
            );
        }
        occurrenceRepository.flush();
        return affected;
    }

    private ChoreOccurrence lockedOccurrence(String occurrencePublicId) {
        String requiredOccurrencePublicId =
                Objects.requireNonNull(occurrencePublicId, "회차 공개 ID는 필수입니다.");
        Long groupId = occurrenceRepository.findGroupIdByPublicId(requiredOccurrencePublicId)
                .orElseThrow(() -> new OccurrenceNotFoundException(requiredOccurrencePublicId));
        sharingGroupRepository.findByIdForUpdate(groupId)
                .orElseThrow(() -> new IllegalStateException("회차의 그룹을 찾을 수 없습니다."));
        return occurrenceRepository
                .findByPublicIdForUpdate(
                        requiredOccurrencePublicId
                )
                .orElseThrow(() -> new OccurrenceNotFoundException(requiredOccurrencePublicId));
    }

    private GroupMember requireActorOfOccurrence(
            String actorMemberPublicId,
            ChoreOccurrence occurrence
    ) {
        GroupMember actor = groupMemberRepository
                .findByPublicIdForUpdate(
                        Objects.requireNonNull(actorMemberPublicId, "멤버 공개 ID는 필수입니다.")
                )
                .orElseThrow(() -> new MemberNotFoundException(actorMemberPublicId));
        if (actor.getGroup().getId() == null
                || !actor.getGroup().getId().equals(occurrence.getChore().getGroup().getId())) {
            throw new OccurrenceCommandConflictException("회차와 같은 그룹의 멤버만 처리할 수 있습니다.");
        }
        if (!actor.isActive()) {
            throw new OccurrenceCommandConflictException("탈퇴한 멤버는 회차를 처리할 수 없습니다.");
        }
        return actor;
    }

    private void requireCurrentAssignee(ChoreOccurrence occurrence, GroupMember actor) {
        if (occurrence.getStatus() != OccurrenceStatus.ASSIGNED) {
            throw new OccurrenceCommandConflictException(
                    "현재 배정 상태의 회차만 처리할 수 있습니다."
            );
        }
        GroupMember assignee = occurrence.currentAssignee()
                .orElseThrow(() -> new IllegalStateException("배정 상태에 현재 담당자가 없습니다."));
        if (!assignee.getId().equals(actor.getId())) {
            throw new OccurrenceCommandConflictException("현재 담당자만 이 작업을 처리할 수 있습니다.");
        }
    }

    private void requireTerminalActor(
            ChoreOccurrence occurrence,
            GroupMember actor,
            AssignmentEndReason expectedEndReason
    ) {
        var lastAssignment = assignmentRepository
                .findFirstByOccurrence_IdOrderBySequenceNumberDesc(occurrence.getId())
                .orElseThrow(() -> new IllegalStateException("종료 회차에 배정 이력이 없습니다."));
        if (!lastAssignment.getAssignee().getId().equals(actor.getId())
                || lastAssignment.getEndReason() != expectedEndReason) {
            throw new OccurrenceCommandConflictException(
                    "최초 요청을 처리한 담당자만 같은 종료 요청을 다시 보낼 수 있습니다."
            );
        }
    }

    private long eligibleCandidateCount(ChoreOccurrence occurrence) {
        return eligibilityRepository
                .findAllByOccurrence_IdAndSnapshotVersionOrderById(
                        occurrence.getId(),
                        occurrence.getEligibilitySnapshotVersion()
                )
                .stream()
                .map(snapshot -> snapshot.getMember())
                .filter(GroupMember::isActive)
                .filter(member -> !assignmentRepository
                        .existsByOccurrence_IdAndAssignee_IdAndEndReasonIn(
                                occurrence.getId(),
                                member.getId(),
                                SAME_OCCURRENCE_EXCLUSIONS
                        ))
                .count();
    }
}
