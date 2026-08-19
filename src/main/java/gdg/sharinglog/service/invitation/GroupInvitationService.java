package gdg.sharinglog.service.invitation;

import java.time.Duration;
import java.time.Instant;

import gdg.sharinglog.domain.GroupInvitation;
import gdg.sharinglog.domain.GroupMember;
import gdg.sharinglog.domain.GroupRole;
import gdg.sharinglog.domain.MemberStatus;
import gdg.sharinglog.domain.SharingGroup;
import gdg.sharinglog.domain.User;
import gdg.sharinglog.repository.GroupInvitationRepository;
import gdg.sharinglog.repository.GroupMemberRepository;
import gdg.sharinglog.repository.SharingGroupRepository;
import gdg.sharinglog.service.group.exception.GroupNotFoundException;
import gdg.sharinglog.service.invitation.exception.InvitationPermissionDeniedException;
import gdg.sharinglog.service.invitation.result.IssuedInvitation;
import gdg.sharinglog.service.user.AuthenticatedUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GroupInvitationService {

    private static final Duration INVITATION_VALIDITY = Duration.ofHours(24);
    private static final int MAX_CODE_GENERATION_ATTEMPTS = 10;

    private final SharingGroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final GroupInvitationRepository invitationRepository;
    private final InvitationCodeGenerator codeGenerator;
    private final InvitationCodeHasher codeHasher;
    private final AuthenticatedUserService authenticatedUserService;

    @Transactional
    public IssuedInvitation issue(Long groupId, String registrationId, OAuth2User oAuth2User) {
        User requester = authenticatedUserService.requireUser(registrationId, oAuth2User);
        SharingGroup group = groupRepository.findById(groupId)
                .orElseThrow(() -> new GroupNotFoundException(groupId));
        requireOwner(group, requester);
        return issue(group, requester, Instant.now());
    }

    @Transactional
    public IssuedInvitation reissue(
            String groupPublicId,
            String registrationId,
            OAuth2User oAuth2User
    ) {
        User requester = authenticatedUserService.requireUser(registrationId, oAuth2User);
        SharingGroup group = groupRepository.findByPublicIdForUpdate(groupPublicId)
                .orElseThrow(() -> new GroupNotFoundException(groupPublicId));
        requireOwner(group, requester);
        return issue(group, requester, Instant.now());
    }

    private IssuedInvitation issue(SharingGroup group, User requester, Instant createdAt) {
        String code = nextUniqueCode();
        GroupInvitation invitation = invitationRepository.save(new GroupInvitation(
                group,
                requester,
                codeHasher.hash(code),
                createdAt,
                createdAt.plus(INVITATION_VALIDITY)
        ));

        return new IssuedInvitation(
                invitation.getId(),
                group.getId(),
                code,
                invitation.getCreatedAt(),
                invitation.getExpiresAt()
        );
    }

    private void requireOwner(SharingGroup group, User requester) {
        GroupMember membership = groupMemberRepository
                .findByGroup_IdAndUser_IdAndStatus(
                        group.getId(),
                        requester.getId(),
                        MemberStatus.ACTIVE
                )
                .orElseThrow(InvitationPermissionDeniedException::new);

        if (membership.getRole() != GroupRole.OWNER) {
            throw new InvitationPermissionDeniedException();
        }
    }

    private String nextUniqueCode() {
        for (int attempt = 0; attempt < MAX_CODE_GENERATION_ATTEMPTS; attempt++) {
            String code = codeGenerator.generate();
            if (!invitationRepository.existsByCodeHash(codeHasher.hash(code))) {
                return code;
            }
        }
        throw new IllegalStateException("고유한 초대 코드를 생성하지 못했습니다.");
    }
}
