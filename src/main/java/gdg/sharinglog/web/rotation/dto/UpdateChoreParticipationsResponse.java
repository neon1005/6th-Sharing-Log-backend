package gdg.sharinglog.web.rotation.dto;

import java.util.List;

import gdg.sharinglog.service.rotation.api.member.ChoreParticipationApplicationScope;
import gdg.sharinglog.service.rotation.api.member.UpdatedChoreParticipations;

public record UpdateChoreParticipationsResponse(
        String membershipId,
        ChoreParticipationApplicationScope applicationScope,
        List<ChoreChange> chores
) {

    public UpdateChoreParticipationsResponse {
        chores = List.copyOf(chores);
    }

    public record ChoreChange(
            String choreId,
            UpdatedChoreParticipations.Action action,
            boolean changed,
            long version,
            int rebuiltOccurrenceCount,
            int reassignedOccurrenceCount,
            int needsAttentionOccurrenceCount
    ) {
    }
}
