package gdg.sharinglog.service.rotation.api.chore;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Objects;

import gdg.sharinglog.domain.rotation.ChoreEligibilityMode;
import gdg.sharinglog.domain.rotation.ChoreFrequency;

public record UpdateChoreCommand(
        String name,
        Schedule schedule,
        Eligibility eligibility
) {

    public record Schedule(
            ChoreFrequency frequency,
            LocalTime dueTime,
            DayOfWeek weeklyDueDay,
            LocalDate biweeklyAnchorDate
    ) {
    }

    public record Eligibility(
            ChoreEligibilityMode mode,
            List<String> eligibleMembershipPublicIds
    ) {

        public Eligibility {
            eligibleMembershipPublicIds = List.copyOf(Objects.requireNonNull(
                    eligibleMembershipPublicIds,
                    "Eligible membership IDs are required."
            ));
        }
    }
}
