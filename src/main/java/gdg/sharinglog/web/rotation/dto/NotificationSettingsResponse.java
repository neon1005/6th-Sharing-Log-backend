package gdg.sharinglog.web.rotation.dto;

import gdg.sharinglog.domain.GroupMember;

public record NotificationSettingsResponse(
        String groupId,
        String membershipId,
        int dailyHoursBeforeDue,
        int weeklyHoursBeforeDue,
        int biweeklyHoursBeforeDue
) {

    public static NotificationSettingsResponse from(GroupMember membership) {
        return new NotificationSettingsResponse(
                membership.getGroup().getPublicId(),
                membership.getPublicId(),
                membership.getDailyDueSoonHours(),
                membership.getWeeklyDueSoonHours(),
                membership.getBiweeklyDueSoonHours()
        );
    }
}
