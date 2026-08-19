package gdg.sharinglog.web.dto;

public record UpdateNotificationPreferencesRequest(
        Boolean dueSoonEnabled,
        Boolean choreCompletedEnabled,
        Boolean noticeEnabled
) {
}
