package gdg.sharinglog.web.rotation.dto;

import java.time.LocalDate;
import java.util.List;

import gdg.sharinglog.domain.rotation.ChoreFrequency;

public record OccurrenceListResponse(
        String groupId,
        ChoreFrequency frequency,
        QueryResponse query,
        List<OccurrenceSummaryResponse> items,
        String nextCursor,
        boolean hasNext
) {

    public record QueryResponse(
            LocalDate activeOn,
            String timeZoneId
    ) {
    }
}
