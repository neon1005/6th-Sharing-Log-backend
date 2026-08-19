package gdg.sharinglog.service.booking;

import java.util.Objects;

import gdg.sharinglog.domain.GroupMember;
import gdg.sharinglog.domain.SharingGroup;

public record BookingActor(
        SharingGroup group,
        GroupMember membership
) {

    public BookingActor {
        Objects.requireNonNull(group, "group must not be null");
        Objects.requireNonNull(membership, "membership must not be null");
    }
}
