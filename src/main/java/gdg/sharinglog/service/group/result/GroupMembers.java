package gdg.sharinglog.service.group.result;

import java.time.Instant;
import java.util.List;

import gdg.sharinglog.domain.GroupRole;

public record GroupMembers(
        Long groupId,
        String groupName,
        GroupRole myRole,
        List<Member> members
) {

    public GroupMembers {
        members = List.copyOf(members);
    }

    public record Member(
            String email,
            GroupRole role,
            Instant joinedAt,
            boolean me
    ) {
    }
}
