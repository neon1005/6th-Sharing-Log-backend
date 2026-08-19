package gdg.sharinglog.web.rotation.dto;

import java.util.List;

import gdg.sharinglog.domain.GroupRole;
import gdg.sharinglog.domain.MemberStatus;

public record RotationMemberListResponse(
        String groupId,
        String actorMembershipId,
        boolean canManage,
        List<Member> items
) {

    public record Member(
            String membershipId,
            String displayName,
            GroupRole role,
            MemberStatus status,
            long version
    ) {
    }
}
