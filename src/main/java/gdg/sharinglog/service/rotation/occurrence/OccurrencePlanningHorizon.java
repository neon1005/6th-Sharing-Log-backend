package gdg.sharinglog.service.rotation.occurrence;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Objects;

import gdg.sharinglog.domain.SharingGroup;

public final class OccurrencePlanningHorizon {

    public static final int MAX_WEEK_OFFSET = 4;
    public static final int TOTAL_WEEK_WINDOWS = MAX_WEEK_OFFSET + 1;

    private OccurrencePlanningHorizon() {
    }

    public static LocalDate currentWeekStart(SharingGroup group, LocalDate activeOn) {
        Objects.requireNonNull(group, "Group is required.");
        LocalDate effectiveDate = Objects.requireNonNull(activeOn, "Active date is required.");
        DayOfWeek weekStartsOn = group.getWeekStartsOn();
        int daysSinceWeekStart = Math.floorMod(
                effectiveDate.getDayOfWeek().getValue() - weekStartsOn.getValue(),
                7
        );
        return effectiveDate.minusDays(daysSinceWeekStart);
    }

    public static LocalDate horizonEndExclusive(SharingGroup group, LocalDate activeOn) {
        return currentWeekStart(group, activeOn).plusWeeks(TOTAL_WEEK_WINDOWS);
    }

    public static WeekWindow weekWindow(
            SharingGroup group,
            LocalDate activeOn,
            int weekOffset
    ) {
        if (weekOffset < 0 || weekOffset > MAX_WEEK_OFFSET) {
            throw new IllegalArgumentException("weekOffset must be between 0 and 4.");
        }
        LocalDate fromInclusive = currentWeekStart(group, activeOn).plusWeeks(weekOffset);
        return new WeekWindow(fromInclusive, fromInclusive.plusWeeks(1));
    }

    public record WeekWindow(
            LocalDate fromInclusive,
            LocalDate toExclusive
    ) {
        public WeekWindow {
            Objects.requireNonNull(fromInclusive, "Week start is required.");
            Objects.requireNonNull(toExclusive, "Week end is required.");
            if (!toExclusive.equals(fromInclusive.plusWeeks(1))) {
                throw new IllegalArgumentException("A planning window must contain exactly seven days.");
            }
        }

        public boolean overlaps(LocalDate periodStart, LocalDate periodEndExclusive) {
            return periodStart.isBefore(toExclusive)
                    && periodEndExclusive.isAfter(fromInclusive);
        }
    }
}
