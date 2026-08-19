package gdg.sharinglog.service.rotation.api.chore;

import gdg.sharinglog.domain.rotation.ChoreOccurrence;
import gdg.sharinglog.service.rotation.access.RotationActor;

public record CreatedChore(
        ChoreView chore,
        ChoreOccurrence currentOccurrence,
        RotationActor actor
) {
}
