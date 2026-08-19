package gdg.sharinglog.web.rotation.dto;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import gdg.sharinglog.domain.rotation.ChoreEligibilityMode;
import gdg.sharinglog.domain.rotation.ChoreFrequency;

public record ChoreResponse(
        String choreId,
        String groupId,
        String name,
        ScheduleResponse schedule,
        EligibilityResponse eligibility,
        boolean active,
        String createdByMembershipId,
        Instant createdAt,
        long version
) {

    public record ScheduleResponse(
            ChoreFrequency frequency,
            LocalTime dueTime,
            DayOfWeek weeklyDueDay,
            LocalDate biweeklyAnchorDate
    ) {
    }

    public record EligibilityResponse(
            ChoreEligibilityMode mode,
            List<MemberRefResponse> members
    ) {
    }
}
