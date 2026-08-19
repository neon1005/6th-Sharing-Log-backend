package gdg.sharinglog.web.booking.dto;

import java.time.LocalDate;
import java.util.List;

public record ReservationListResponse(
        String groupId,
        String spaceId,
        String spaceName,
        LocalDate date,
        List<ReservationResponse> items
) {

    public ReservationListResponse {
        items = List.copyOf(items);
    }
}
