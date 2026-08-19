package gdg.sharinglog.service.invitation.result;

import java.time.Instant;

import gdg.sharinglog.domain.GroupRole;

public record InvitationPreview(
        Long groupId,
        String groupPublicId,
        String groupName,
        Instant expiresAt,
        boolean alreadyMember,
        GroupRole currentRole
) {
}
