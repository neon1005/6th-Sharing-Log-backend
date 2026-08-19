package gdg.sharinglog.web.dto;

import gdg.sharinglog.domain.GroupRole;
import gdg.sharinglog.service.group.result.MyGroup;

public record MyGroupResponse(
        String groupPublicId,
        String membershipPublicId,
        long membershipVersion,
        String groupName,
        String groupAddress,
        GroupRole role
) {

    public static MyGroupResponse from(MyGroup myGroup) {
        return new MyGroupResponse(
                myGroup.groupPublicId(),
                myGroup.membershipPublicId(),
                myGroup.membershipVersion(),
                myGroup.groupName(),
                myGroup.groupAddress(),
                myGroup.role()
        );
    }
}
