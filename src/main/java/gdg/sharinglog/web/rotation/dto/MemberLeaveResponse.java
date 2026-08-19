package gdg.sharinglog.web.rotation.dto;

import java.time.Instant;
import java.util.List;

import gdg.sharinglog.domain.MemberStatus;
import gdg.sharinglog.domain.rotation.OccurrenceStatus;

public record MemberLeaveResponse(
        Member member,
        ReassignmentSummary reassignmentSummary,
        int terminalOccurrencesChanged
) {

    public record Member(
            String membershipId,
            String displayName,
            MemberStatus status,
            Instant leftAt,
            long version
    ) {
    }

    public record ReassignmentSummary(
            int processedCount,
            int reassignedCount,
            int needsAttentionCount,
            List<ReassignedOccurrence> occurrences
    ) {
    }

    public record ReassignedOccurrence(
            String occurrenceId,
            OccurrenceStatus status,
            MemberRefResponse newAssignee,
            long version
    ) {
    }
}
