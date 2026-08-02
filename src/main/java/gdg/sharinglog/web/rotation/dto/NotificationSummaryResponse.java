package gdg.sharinglog.web.rotation.dto;

public record NotificationSummaryResponse(
        int dueSoonCount,
        int pendingSubstituteRequestCount,
        int unreadCount
) {
}
