package gdg.sharinglog.web.dto;

import java.time.Instant;
import java.util.List;

import gdg.sharinglog.domain.GroupRole;
import gdg.sharinglog.service.group.result.GroupMembers;

public record GroupMembersResponse(
        Long groupId,
        String groupName,
        GroupRole myRole,
        List<MemberResponse> members
) {

    public static GroupMembersResponse from(GroupMembers groupMembers) {
        return new GroupMembersResponse(
                groupMembers.groupId(),
                groupMembers.groupName(),
                groupMembers.myRole(),
                groupMembers.members().stream()
                        .map(MemberResponse::from)
                        .toList()
        );
    }

    public record MemberResponse(
            String email,
            GroupRole role,
            Instant joinedAt,
            boolean me
    ) {

        private static MemberResponse from(GroupMembers.Member member) {
            return new MemberResponse(
                    member.email(),
                    member.role(),
                    member.joinedAt(),
                    member.me()
            );
        }
    }
}
