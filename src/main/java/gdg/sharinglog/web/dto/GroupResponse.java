package gdg.sharinglog.web.dto;

import java.time.Instant;

import gdg.sharinglog.domain.GroupRole;
import gdg.sharinglog.service.group.result.CreatedGroup;

public record GroupResponse(
        Long groupId,
        String groupPublicId,
        String name,
        String address,
        Long membershipId,
        String membershipPublicId,
        GroupRole role,
        Instant createdAt
) {

    public static GroupResponse from(CreatedGroup group) {
        return new GroupResponse(
                group.groupId(),
                group.groupPublicId(),
                group.name(),
                group.address(),
                group.membershipId(),
                group.membershipPublicId(),
                group.role(),
                group.createdAt()
        );
    }
}
