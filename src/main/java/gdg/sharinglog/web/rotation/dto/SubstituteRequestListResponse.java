package gdg.sharinglog.web.rotation.dto;

import java.util.List;

import gdg.sharinglog.service.rotation.api.substitute.SubstituteRequestBox;

public record SubstituteRequestListResponse(
        String groupId,
        SubstituteRequestBox box,
        List<SubstituteRequestResponse> items,
        int totalCount
) {

    public SubstituteRequestListResponse {
        items = List.copyOf(items);
    }
}
