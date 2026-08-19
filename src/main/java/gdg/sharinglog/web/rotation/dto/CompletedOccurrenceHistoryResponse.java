package gdg.sharinglog.web.rotation.dto;

import java.util.List;

public record CompletedOccurrenceHistoryResponse(
        String groupId,
        boolean mineOnly,
        List<OccurrenceSummaryResponse> items,
        int totalCount
) {

    public CompletedOccurrenceHistoryResponse {
        items = List.copyOf(items);
    }
}
