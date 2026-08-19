package gdg.sharinglog.service.invitation.result;

import java.time.Instant;

public record IssuedInvitation(
        Long invitationId,
        Long groupId,
        String code,
        Instant createdAt,
        Instant expiresAt
) {
}
