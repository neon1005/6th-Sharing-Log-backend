package gdg.sharinglog.service.group;

import java.util.Comparator;
import java.util.List;

import gdg.sharinglog.domain.GroupMember;
import gdg.sharinglog.domain.GroupRole;
import gdg.sharinglog.domain.MemberStatus;
import gdg.sharinglog.domain.SharingGroup;
import gdg.sharinglog.domain.User;
import gdg.sharinglog.repository.GroupMemberRepository;
import gdg.sharinglog.repository.SharingGroupRepository;
import gdg.sharinglog.service.group.exception.GroupMemberAccessDeniedException;
import gdg.sharinglog.service.group.exception.GroupNotFoundException;
import gdg.sharinglog.service.group.result.GroupMembers;
import gdg.sharinglog.service.group.result.MyGroup;
import gdg.sharinglog.service.user.AuthenticatedUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GroupMemberQueryService {

    private static final Comparator<GroupMember> MEMBER_ORDER = Comparator
            .comparingInt((GroupMember member) -> member.getRole() == GroupRole.OWNER ? 0 : 1)
            .thenComparing(GroupMember::getJoinedAt)
            .thenComparing(GroupMember::getId);

    private final SharingGroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final AuthenticatedUserService authenticatedUserService;

    @Transactional(readOnly = true)
    public GroupMembers findMembers(Long groupId, String registrationId, OAuth2User oAuth2User) {
        User requester = authenticatedUserService.requireUser(registrationId, oAuth2User);
        SharingGroup group = groupRepository.findById(groupId)
                .orElseThrow(() -> new GroupNotFoundException(groupId));
        GroupMember requesterMembership = groupMemberRepository
                .findByGroup_IdAndUser_IdAndStatus(
                        groupId,
                        requester.getId(),
                        MemberStatus.ACTIVE
                )
                .orElseThrow(GroupMemberAccessDeniedException::new);

        List<GroupMembers.Member> members = groupMemberRepository
                .findAllByGroup_IdAndStatusOrderById(groupId, MemberStatus.ACTIVE)
                .stream()
                .sorted(MEMBER_ORDER)
                .map(membership -> toMember(membership, requester.getId()))
                .toList();

        return new GroupMembers(
                group.getId(),
                group.getName(),
                requesterMembership.getRole(),
                members
        );
    }

    @Transactional(readOnly = true)
    public List<MyGroup> findMyGroups(String registrationId, OAuth2User oAuth2User) {
        User user = authenticatedUserService.requireUser(registrationId, oAuth2User);
        return groupMemberRepository
                .findAllByUser_IdAndStatus(user.getId(), MemberStatus.ACTIVE)
                .stream()
                .map(membership -> new MyGroup(
                        membership.getGroup().getPublicId(),
                        membership.getPublicId(),
                        membership.getVersion(),
                        membership.getGroup().getName(),
                        membership.getGroup().getAddress(),
                        membership.getRole()
                ))
                .toList();
    }

    private GroupMembers.Member toMember(GroupMember membership, Long requesterId) {
        User member = membership.getUser();
        return new GroupMembers.Member(
                member.getEmail(),
                membership.getRole(),
                membership.getJoinedAt(),
                member.getId().equals(requesterId)
        );
    }
}
