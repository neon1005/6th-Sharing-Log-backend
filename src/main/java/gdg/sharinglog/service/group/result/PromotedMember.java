package gdg.sharinglog.service.group.result;

import gdg.sharinglog.domain.GroupRole;

public record PromotedMember(
        String membershipPublicId,
        GroupRole role
) {
}
