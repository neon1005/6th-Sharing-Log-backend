package gdg.sharinglog.service.rotation.access;

import gdg.sharinglog.domain.GroupMember;
import gdg.sharinglog.domain.GroupRole;
import gdg.sharinglog.domain.MemberStatus;
import gdg.sharinglog.domain.SharingGroup;
import gdg.sharinglog.domain.User;
import gdg.sharinglog.repository.GroupMemberRepository;
import gdg.sharinglog.repository.SharingGroupRepository;
import gdg.sharinglog.service.user.AuthenticatedUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RotationActorAccessService {

    private final SharingGroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final AuthenticatedUserService authenticatedUserService;

    @Transactional(readOnly = true)
    public RotationActor requireActiveMember(String groupPublicId,
                                             String registrationId,
                                             OAuth2User oAuth2User) {
        User user = authenticatedUserService.requireUser(registrationId, oAuth2User);
        SharingGroup group = groupRepository.findByPublicId(groupPublicId)
                .orElseThrow(() -> new RotationGroupNotFoundException(groupPublicId));
        return activeActor(group, user);
    }

    @Transactional
    public RotationActor requireActiveMemberForUpdate(String groupPublicId,
                                                      String registrationId,
                                                      OAuth2User oAuth2User) {
        User user = authenticatedUserService.requireUser(registrationId, oAuth2User);
        SharingGroup group = groupRepository.findByPublicIdForUpdate(groupPublicId)
                .orElseThrow(() -> new RotationGroupNotFoundException(groupPublicId));
        return activeActor(group, user);
    }

    @Transactional(readOnly = true)
    public RotationActor requireOwner(String groupPublicId,
                                      String registrationId,
                                      OAuth2User oAuth2User) {
        return requireOwner(requireActiveMember(groupPublicId, registrationId, oAuth2User));
    }

    @Transactional
    public RotationActor requireOwnerForUpdate(String groupPublicId,
                                               String registrationId,
                                               OAuth2User oAuth2User) {
        return requireOwner(requireActiveMemberForUpdate(
                groupPublicId,
                registrationId,
                oAuth2User
        ));
    }

    @Transactional(readOnly = true)
    public GroupMember requireActiveTargetMember(RotationActor actor,
                                                 String membershipPublicId) {
        requireActiveActor(actor);
        return groupMemberRepository.findByPublicIdAndGroup_PublicIdAndStatus(
                        membershipPublicId,
                        actor.group().getPublicId(),
                        MemberStatus.ACTIVE
                )
                .orElseThrow(() -> new RotationMemberNotFoundException(membershipPublicId));
    }

    @Transactional
    public GroupMember requireActiveTargetMemberForUpdate(RotationActor actor,
                                                          String membershipPublicId) {
        requireActiveActor(actor);
        return groupMemberRepository.findByPublicIdAndGroupPublicIdAndStatusForUpdate(
                        membershipPublicId,
                        actor.group().getPublicId(),
                        MemberStatus.ACTIVE
                )
                .orElseThrow(() -> new RotationMemberNotFoundException(membershipPublicId));
    }

    @Transactional
    public GroupMember requireTargetMemberForUpdate(RotationActor actor,
                                                    String membershipPublicId) {
        requireActiveActor(actor);
        return groupMemberRepository.findByPublicIdAndGroupPublicIdForUpdate(
                        membershipPublicId,
                        actor.group().getPublicId()
                )
                .orElseThrow(() -> new RotationMemberNotFoundException(membershipPublicId));
    }

    private RotationActor activeActor(SharingGroup group, User user) {
        GroupMember membership = groupMemberRepository
                .findByGroup_IdAndUser_IdAndStatus(
                        group.getId(),
                        user.getId(),
                        MemberStatus.ACTIVE
                )
                .orElseThrow(RotationAccessDeniedException::new);
        return new RotationActor(group, membership, user);
    }

    private RotationActor requireOwner(RotationActor actor) {
        if (actor.membership().getRole() != GroupRole.OWNER) {
            throw new RotationAccessDeniedException("Group owner permission is required.");
        }
        return actor;
    }

    private void requireActiveActor(RotationActor actor) {
        if (!actor.membership().isActive()
                || !sameGroup(actor)
                || !sameUser(actor)) {
            throw new RotationAccessDeniedException();
        }
    }

    private boolean sameGroup(RotationActor actor) {
        return actor.membership().getGroup().getPublicId()
                .equals(actor.group().getPublicId());
    }

    private boolean sameUser(RotationActor actor) {
        return actor.membership().getUser().getId().equals(actor.user().getId());
    }
}
