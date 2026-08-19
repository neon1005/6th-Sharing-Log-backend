package gdg.sharinglog.service.rotation.api.member;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import gdg.sharinglog.domain.GroupMember;
import gdg.sharinglog.domain.rotation.ChoreOccurrence;
import gdg.sharinglog.domain.rotation.OccurrenceStatus;
import gdg.sharinglog.service.rotation.exception.LastOwnerCannotLeaveException;
import gdg.sharinglog.service.rotation.occurrence.OccurrenceCommandService;
import gdg.sharinglog.service.rotation.occurrence.OccurrencePlanService;
import gdg.sharinglog.service.rotation.access.RotationActor;
import gdg.sharinglog.service.rotation.access.RotationActorAccessService;
import gdg.sharinglog.web.rotation.RotationViewMapper;
import gdg.sharinglog.web.rotation.dto.MemberLeaveResponse;
import gdg.sharinglog.web.rotation.dto.MemberRefResponse;
import gdg.sharinglog.web.rotation.error.RotationConflictException;
import gdg.sharinglog.web.rotation.error.RotationForbiddenException;
import gdg.sharinglog.web.rotation.error.RotationProblemCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemberLeaveApplicationService {

    private final RotationActorAccessService accessService;
    private final OccurrenceCommandService commandService;
    private final OccurrencePlanService occurrencePlanService;
    private final RotationViewMapper viewMapper;

    @Transactional
    public MemberLeaveResponse leave(
            String groupPublicId,
            String membershipPublicId,
            String registrationId,
            OAuth2User principal,
            long expectedVersion,
            Instant leftAt
    ) {
        RotationActor actor =
                accessService.requireActiveMemberForUpdate(groupPublicId, registrationId, principal);
        GroupMember target =
                accessService.requireTargetMemberForUpdate(actor, membershipPublicId);

        if (!target.isActive()) {
            throw new RotationConflictException(
                    RotationProblemCode.MEMBER_ALREADY_LEFT,
                    "This member has already left the group.",
                    Map.of("resourceId", membershipPublicId)
            );
        }
        requireVersion(target, expectedVersion);
        boolean self = actor.membership().getId().equals(target.getId());
        if (!self && !actor.isOwner()) {
            throw new RotationForbiddenException(
                    "Only the member or a group owner can remove this membership."
            );
        }

        List<ChoreOccurrence> affected;
        try {
            affected = commandService.leaveMember(membershipPublicId, leftAt);
            occurrencePlanService.regenerateGroupFuture(actor.group().getId(), leftAt);
        } catch (LastOwnerCannotLeaveException exception) {
            throw new RotationConflictException(
                    RotationProblemCode.LAST_OWNER_CANNOT_LEAVE,
                    exception.getMessage()
            );
        }

        MemberRefResponse memberRef = viewMapper.member(target);
        List<MemberLeaveResponse.ReassignedOccurrence> occurrences = affected.stream()
                .map(item -> new MemberLeaveResponse.ReassignedOccurrence(
                        item.getPublicId(),
                        item.getStatus(),
                        item.currentAssignee().map(viewMapper::member).orElse(null),
                        item.getVersion()
                ))
                .toList();
        int reassigned = (int) affected.stream()
                .filter(item -> item.getStatus() == OccurrenceStatus.ASSIGNED)
                .count();
        int needsAttention = affected.size() - reassigned;

        return new MemberLeaveResponse(
                new MemberLeaveResponse.Member(
                        target.getPublicId(),
                        memberRef.displayName(),
                        target.getStatus(),
                        target.getLeftAt(),
                        target.getVersion()
                ),
                new MemberLeaveResponse.ReassignmentSummary(
                        affected.size(),
                        reassigned,
                        needsAttention,
                        occurrences
                ),
                0
        );
    }

    private void requireVersion(GroupMember member, long expectedVersion) {
        if (member.getVersion() != expectedVersion) {
            throw new RotationConflictException(
                    RotationProblemCode.VERSION_CONFLICT,
                    "The membership changed. Reload it and try again.",
                    Map.of(
                            "resourceId", member.getPublicId(),
                            "expectedVersion", expectedVersion,
                            "currentVersion", member.getVersion()
                    )
            );
        }
    }
}
