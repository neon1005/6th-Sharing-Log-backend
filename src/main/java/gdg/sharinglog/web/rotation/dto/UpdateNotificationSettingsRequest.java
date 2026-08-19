package gdg.sharinglog.web.rotation.dto;

import gdg.sharinglog.domain.GroupMember;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateNotificationSettingsRequest(
        @NotNull(message = "매일 마감 임박 알림 시간은 필수입니다.")
        @Min(value = 1, message = "매일 마감 임박 알림 시간은 1시간 이상이어야 합니다.")
        @Max(
                value = GroupMember.MAX_DAILY_DUE_SOON_HOURS,
                message = "매일 마감 임박 알림 시간은 24시간 이하여야 합니다."
        )
        Integer dailyHoursBeforeDue,

        @NotNull(message = "매주 마감 임박 알림 시간은 필수입니다.")
        @Min(value = 1, message = "매주 마감 임박 알림 시간은 1시간 이상이어야 합니다.")
        @Max(
                value = GroupMember.MAX_WEEKLY_DUE_SOON_HOURS,
                message = "매주 마감 임박 알림 시간은 168시간 이하여야 합니다."
        )
        Integer weeklyHoursBeforeDue,

        @NotNull(message = "격주 마감 임박 알림 시간은 필수입니다.")
        @Min(value = 1, message = "격주 마감 임박 알림 시간은 1시간 이상이어야 합니다.")
        @Max(
                value = GroupMember.MAX_BIWEEKLY_DUE_SOON_HOURS,
                message = "격주 마감 임박 알림 시간은 336시간 이하여야 합니다."
        )
        Integer biweeklyHoursBeforeDue
) {
}
