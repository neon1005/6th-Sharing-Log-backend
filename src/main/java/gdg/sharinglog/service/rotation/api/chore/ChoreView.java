package gdg.sharinglog.service.rotation.api.chore;

import java.util.List;

import gdg.sharinglog.domain.GroupMember;
import gdg.sharinglog.domain.rotation.Chore;

public record ChoreView(
        Chore chore,
        List<GroupMember> eligibleMembers
) {

    public ChoreView {
        eligibleMembers = List.copyOf(eligibleMembers);
    }
}
