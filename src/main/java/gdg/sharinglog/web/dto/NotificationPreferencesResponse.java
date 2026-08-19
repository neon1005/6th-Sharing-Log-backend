package gdg.sharinglog.web.dto;

import gdg.sharinglog.domain.User;

public record NotificationPreferencesResponse(
        boolean dueSoonEnabled,
        boolean choreCompletedEnabled,
        boolean noticeEnabled
) {

    public static NotificationPreferencesResponse from(User user) {
        return new NotificationPreferencesResponse(
                user.isDueSoonPushEnabled(),
                user.isChoreCompletedPushEnabled(),
                user.isNoticePushEnabled()
        );
    }
}
