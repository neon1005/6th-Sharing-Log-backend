package gdg.sharinglog.web.rotation.dto;

public record CreateChoreResponse(
        ChoreResponse chore,
        OccurrenceSummaryResponse currentOccurrence
) {
}
