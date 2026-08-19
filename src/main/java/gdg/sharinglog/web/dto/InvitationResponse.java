package gdg.sharinglog.web.dto;

import java.time.Instant;

import gdg.sharinglog.service.invitation.result.IssuedInvitation;

public record InvitationResponse(
        Long invitationId,
        Long groupId,
        String code,
        String invitePath,
        String inviteUrl,
        Instant createdAt,
        Instant expiresAt
) {

    public static InvitationResponse from(IssuedInvitation invitation, String inviteUrl) {
        String invitePath = "/invite/" + invitation.code();
        return new InvitationResponse(
                invitation.invitationId(),
                invitation.groupId(),
                invitation.code(),
                invitePath,
                inviteUrl,
                invitation.createdAt(),
                invitation.expiresAt()
        );
    }
}
