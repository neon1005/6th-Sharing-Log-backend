package gdg.sharinglog.service.rotation.access;

import java.util.Objects;

import gdg.sharinglog.domain.GroupMember;
import gdg.sharinglog.domain.GroupRole;
import gdg.sharinglog.domain.SharingGroup;
import gdg.sharinglog.domain.User;

public record RotationActor(
        SharingGroup group,
        GroupMember membership,
        User user
) {

    public RotationActor {
        Objects.requireNonNull(group, "group must not be null");
        Objects.requireNonNull(membership, "membership must not be null");
        Objects.requireNonNull(user, "user must not be null");
    }

    public boolean isOwner() {
        return membership.getRole() == GroupRole.OWNER;
    }
}
