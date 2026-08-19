package gdg.sharinglog.service.invitation.result;

import java.time.Instant;

import gdg.sharinglog.domain.GroupRole;

public record AcceptedInvitation(
        Long groupId,
        String groupPublicId,
        String groupName,
        Long membershipId,
        String membershipPublicId,
        GroupRole role,
        Instant joinedAt,
        boolean joinedNow
) {
}
