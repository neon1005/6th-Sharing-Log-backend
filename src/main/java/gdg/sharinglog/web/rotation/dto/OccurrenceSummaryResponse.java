package gdg.sharinglog.web.rotation.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import gdg.sharinglog.domain.rotation.ChoreFrequency;
import gdg.sharinglog.domain.rotation.OccurrenceStatus;

public record OccurrenceSummaryResponse(
        String occurrenceId,
        String choreId,
        String choreName,
        ChoreFrequency frequency,
        LocalDate periodStart,
        LocalDate periodEndExclusive,
        String timeZoneIdSnapshot,
        Instant dueAt,
        OccurrenceStatus status,
        MemberRefResponse currentAssignee,
        MemberRefResponse lastAssignee,
        MemberRefResponse originalAssignee,
        MemberRefResponse completedBy,
        AttentionResponse attention,
        String substituteRequestNotice,
        List<AvailableAction> availableActions,
        Instant closedAt,
        long version
) {

    public enum AvailableAction {
        COMPLETE,
        REQUEST_SUBSTITUTE,
        RETRY_ASSIGNMENT,
        UNDO_COMPLETE
    }
}
