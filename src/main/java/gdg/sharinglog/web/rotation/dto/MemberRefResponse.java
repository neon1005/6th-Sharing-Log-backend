package gdg.sharinglog.web.rotation.dto;

import gdg.sharinglog.domain.MemberStatus;

public record MemberRefResponse(
        String membershipId,
        String displayName,
        String avatarUrl,
        MemberStatus status
) {
}
