package gdg.sharinglog.web.booking.dto;

import java.util.List;

public record SpaceListResponse(
        String groupId,
        List<SpaceResponse> items
) {

    public SpaceListResponse {
        items = List.copyOf(items);
    }
}
