package gdg.sharinglog.service.group.result;

import java.time.Instant;

import gdg.sharinglog.domain.GroupRole;

public record CreatedGroup(
        Long groupId,
        String groupPublicId,
        String name,
        String address,
        Long membershipId,
        String membershipPublicId,
        GroupRole role,
        Instant createdAt
) {
}
