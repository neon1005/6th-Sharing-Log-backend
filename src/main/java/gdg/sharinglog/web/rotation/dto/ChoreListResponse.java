package gdg.sharinglog.web.rotation.dto;

import java.util.List;

public record ChoreListResponse(
        List<ChoreResponse> items,
        String nextCursor,
        boolean hasNext
) {
}
