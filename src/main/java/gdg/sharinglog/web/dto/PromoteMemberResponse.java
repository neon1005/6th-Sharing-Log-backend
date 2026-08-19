package gdg.sharinglog.web.dto;

import gdg.sharinglog.domain.GroupRole;
import gdg.sharinglog.service.group.result.PromotedMember;

public record PromoteMemberResponse(
        String membershipPublicId,
        GroupRole role
) {

    public static PromoteMemberResponse from(PromotedMember promotedMember) {
        return new PromoteMemberResponse(
                promotedMember.membershipPublicId(),
                promotedMember.role()
        );
    }
}
