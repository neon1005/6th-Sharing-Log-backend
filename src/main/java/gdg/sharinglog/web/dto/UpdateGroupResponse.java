package gdg.sharinglog.web.dto;

import gdg.sharinglog.service.group.result.UpdatedGroup;

public record UpdateGroupResponse(
        String groupPublicId,
        String name,
        String address
) {

    public static UpdateGroupResponse from(UpdatedGroup group) {
        return new UpdateGroupResponse(
                group.groupPublicId(),
                group.name(),
                group.address()
        );
    }
}
