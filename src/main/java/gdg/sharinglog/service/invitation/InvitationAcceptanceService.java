package gdg.sharinglog.service.invitation;

import java.time.Instant;
import java.util.Optional;
import java.util.regex.Pattern;

import gdg.sharinglog.domain.GroupInvitation;
import gdg.sharinglog.domain.GroupMember;
import gdg.sharinglog.domain.MemberStatus;
import gdg.sharinglog.domain.SharingGroup;
import gdg.sharinglog.domain.User;
import gdg.sharinglog.repository.GroupInvitationRepository;
import gdg.sharinglog.repository.GroupMemberRepository;
import gdg.sharinglog.repository.SharingGroupRepository;
import gdg.sharinglog.service.invitation.exception.InvitationNotFoundException;
import gdg.sharinglog.service.invitation.exception.InvitationUnavailableException;
import gdg.sharinglog.service.invitation.result.AcceptedInvitation;
import gdg.sharinglog.service.invitation.result.InvitationPreview;
import gdg.sharinglog.service.rotation.enrollment.ChoreEnrollmentService;
import gdg.sharinglog.service.rotation.occurrence.OccurrencePlanService;
import gdg.sharinglog.service.user.AuthenticatedUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InvitationAcceptanceService {

    private static final Pattern INVITATION_CODE_PATTERN = Pattern.compile("[A-Za-z0-9_-]{22}");

    private final GroupInvitationRepository invitationRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final SharingGroupRepository sharingGroupRepository;
    private final InvitationCodeHasher codeHasher;
    private final AuthenticatedUserService authenticatedUserService;
    private final ChoreEnrollmentService choreEnrollmentService;
    private final OccurrencePlanService occurrencePlanService;

    @Transactional(readOnly = true)
    public InvitationPreview preview(String rawCode, String registrationId, OAuth2User oAuth2User) {
        GroupInvitation invitation = requireUsableInvitation(rawCode, Instant.now());
        User user = authenticatedUserService.requireUser(registrationId, oAuth2User);
        SharingGroup group = invitation.getGroup();
        Optional<GroupMember> membership = groupMemberRepository
                .findByGroup_IdAndUser_IdAndStatus(
                        group.getId(),
                        user.getId(),
                        MemberStatus.ACTIVE
                );

        return new InvitationPreview(
                group.getId(),
                group.getPublicId(),
                group.getName(),
                invitation.getExpiresAt(),
                membership.isPresent(),
                membership.map(GroupMember::getRole).orElse(null)
        );
    }

    @Transactional
    public AcceptedInvitation accept(String rawCode, String registrationId, OAuth2User oAuth2User) {
        String codeHash = requireCodeHash(rawCode);
        User user = authenticatedUserService.requireUserForUpdate(registrationId, oAuth2User);
        GroupInvitation invitation = invitationRepository.findByCodeHash(codeHash)
                .orElseThrow(InvitationNotFoundException::new);
        requireUsable(invitation, Instant.now());

        SharingGroup group = sharingGroupRepository
                .findByIdForUpdate(invitation.getGroup().getId())
                .orElseThrow(InvitationUnavailableException::new);
        Optional<GroupMember> existingMembership = groupMemberRepository
                .findByGroup_IdAndUser_Id(group.getId(), user.getId());
        Instant acceptedAt = Instant.now();
        if (existingMembership.isPresent() && existingMembership.get().isActive()) {
            return acceptance(existingMembership.get(), false);
        }
        if (existingMembership.isPresent()) {
            GroupMember membership = existingMembership.get();
            membership.reactivate(acceptedAt);
            GroupMember reactivated = groupMemberRepository.saveAndFlush(membership);
            choreEnrollmentService.activateMemberEnrollments(reactivated, acceptedAt);
            occurrencePlanService.regenerateGroupFuture(group.getId(), acceptedAt);
            return acceptance(reactivated, true);
        }

        GroupMember membership =
                groupMemberRepository.saveAndFlush(GroupMember.member(group, user));
        choreEnrollmentService.activateMemberEnrollments(membership, acceptedAt);
        occurrencePlanService.regenerateGroupFuture(group.getId(), acceptedAt);
        return acceptance(membership, true);
    }

    private GroupInvitation requireUsableInvitation(String rawCode, Instant now) {
        GroupInvitation invitation = invitationRepository.findByCodeHash(requireCodeHash(rawCode))
                .orElseThrow(InvitationNotFoundException::new);
        requireUsable(invitation, now);
        return invitation;
    }

    private String requireCodeHash(String rawCode) {
        if (rawCode == null || !INVITATION_CODE_PATTERN.matcher(rawCode).matches()) {
            throw new InvitationNotFoundException();
        }
        return codeHasher.hash(rawCode);
    }

    private void requireUsable(GroupInvitation invitation, Instant now) {
        if (!invitation.isUsableAt(now)) {
            throw new InvitationUnavailableException();
        }
    }

    private AcceptedInvitation acceptance(GroupMember membership, boolean joinedNow) {
        return new AcceptedInvitation(
                membership.getGroup().getId(),
                membership.getGroup().getPublicId(),
                membership.getGroup().getName(),
                membership.getId(),
                membership.getPublicId(),
                membership.getRole(),
                membership.getJoinedAt(),
                joinedNow
        );
    }
}
