package gdg.sharinglog.service.rotation.api.chore;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import gdg.sharinglog.domain.rotation.ChoreEligibilityMode;
import gdg.sharinglog.domain.rotation.ChoreFrequency;

public record CreateChoreCommand(
        String name,
        ChoreFrequency frequency,
        LocalTime dueTime,
        DayOfWeek weeklyDueDay,
        LocalDate biweeklyAnchorDate,
        ChoreEligibilityMode eligibilityMode,
        List<String> eligibleMembershipPublicIds
) {

    public CreateChoreCommand {
        eligibleMembershipPublicIds = List.copyOf(eligibleMembershipPublicIds);
    }
}
