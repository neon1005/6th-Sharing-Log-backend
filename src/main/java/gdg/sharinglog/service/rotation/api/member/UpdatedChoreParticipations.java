package gdg.sharinglog.service.rotation.api.member;

import java.util.List;

public record UpdatedChoreParticipations(
        String membershipId,
        ChoreParticipationApplicationScope applicationScope,
        List<ChoreChange> chores
) {

    public UpdatedChoreParticipations {
        chores = List.copyOf(chores);
    }

    public enum Action {
        ADD,
        REMOVE
    }

    public record ChoreChange(
            String choreId,
            Action action,
            boolean changed,
            long version,
            int rebuiltOccurrenceCount,
            int reassignedOccurrenceCount,
            int needsAttentionOccurrenceCount
    ) {
    }
}
