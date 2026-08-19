package gdg.sharinglog.web.rotation.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import gdg.sharinglog.domain.rotation.SubstituteRecipientStatus;
import gdg.sharinglog.domain.rotation.SubstituteRequestStatus;

public record SubstituteRequestResponse(
        String requestId,
        SubstituteRequestStatus status,
        String reason,
        MemberRefResponse requester,
        MemberRefResponse acceptedBy,
        String choreId,
        String choreName,
        LocalDate periodStart,
        LocalDate periodEndExclusive,
        Instant dueAt,
        OccurrenceActionResponse.OccurrenceState occurrence,
        List<RecipientResponse> recipients,
        Instant createdAt,
        Instant lastResponseAt,
        Instant resolvedAt,
        long version
) {

    public SubstituteRequestResponse {
        recipients = List.copyOf(recipients);
    }

    public record RecipientResponse(
            MemberRefResponse member,
            SubstituteRecipientStatus status,
            Instant respondedAt
    ) {
    }
}
