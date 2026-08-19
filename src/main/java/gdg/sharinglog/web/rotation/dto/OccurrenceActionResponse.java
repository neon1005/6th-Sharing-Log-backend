package gdg.sharinglog.web.rotation.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;
import gdg.sharinglog.domain.rotation.OccurrenceStatus;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OccurrenceActionResponse(
        Outcome outcome,
        Integer eligibilitySnapshotVersion,
        Long appliedChoreVersion,
        OccurrenceState occurrence
) {

    public enum Outcome {
        COMPLETED,
        SKIPPED,
        REASSIGNED,
        NEEDS_ATTENTION,
        STILL_NEEDS_ATTENTION,
        COMPLETION_UNDONE
    }

    public record OccurrenceState(
            String occurrenceId,
            OccurrenceStatus status,
            MemberRefResponse currentAssignee,
            MemberRefResponse lastAssignee,
            AttentionResponse attention,
            Instant closedAt,
            long version
    ) {
    }
}
