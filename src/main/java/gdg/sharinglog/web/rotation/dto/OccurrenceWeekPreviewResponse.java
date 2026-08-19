package gdg.sharinglog.web.rotation.dto;

import java.time.LocalDate;
import java.util.List;

import gdg.sharinglog.domain.rotation.ChoreFrequency;

public record OccurrenceWeekPreviewResponse(
        String groupId,
        ChoreFrequency frequency,
        int weekOffset,
        LocalDate fromInclusive,
        LocalDate toExclusive,
        String timeZoneId,
        List<OccurrenceSummaryResponse> items
) {
    public OccurrenceWeekPreviewResponse {
        items = List.copyOf(items);
    }
}
