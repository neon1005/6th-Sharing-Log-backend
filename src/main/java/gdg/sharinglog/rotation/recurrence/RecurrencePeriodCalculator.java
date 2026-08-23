package gdg.sharinglog.rotation.recurrence;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

public final class RecurrencePeriodCalculator {

    private static final int DAYS_PER_WEEK = 7;
    private static final int DAYS_PER_BIWEEK = 14;

    public RecurrencePeriod calculate(
            Instant referenceInstant,
            ZoneId groupZoneId,
            RecurrenceRule rule
    ) {
        Objects.requireNonNull(referenceInstant, "referenceInstant must not be null");
        Objects.requireNonNull(groupZoneId, "groupZoneId must not be null");

        LocalDate groupLocalDate = LocalDate.ofInstant(referenceInstant, groupZoneId);
        return calculate(groupLocalDate, rule);
    }

    public RecurrencePeriod calculate(LocalDate referenceDate, RecurrenceRule rule) {
        Objects.requireNonNull(referenceDate, "referenceDate must not be null");
        Objects.requireNonNull(rule, "rule must not be null");

        return switch (rule) {
            case RecurrenceRule.Daily ignored -> daily(referenceDate);
            case RecurrenceRule.Weekly weekly -> weekly(referenceDate, weekly.weekStartsOn());
            case RecurrenceRule.Biweekly biweekly ->
                    biweekly(
                            referenceDate,
                            biweekly.firstDueDate(),
                            biweekly.weekStartsOn()
                    );
        };
    }

    private RecurrencePeriod daily(LocalDate referenceDate) {
        return new RecurrencePeriod(referenceDate, referenceDate.plusDays(1));
    }

    private RecurrencePeriod weekly(LocalDate referenceDate, DayOfWeek weekStartsOn) {
        int daysSinceWeekStart = Math.floorMod(
                referenceDate.getDayOfWeek().getValue() - weekStartsOn.getValue(),
                DAYS_PER_WEEK
        );
        LocalDate periodStart = referenceDate.minusDays(daysSinceWeekStart);
        return new RecurrencePeriod(periodStart, periodStart.plusWeeks(1));
    }

    private RecurrencePeriod biweekly(
            LocalDate referenceDate,
            LocalDate firstDueDate,
            DayOfWeek weekStartsOn
    ) {
        LocalDate firstDueWeekStart = startOfWeek(firstDueDate, weekStartsOn);
        LocalDate firstPeriodStart = firstDueWeekStart.minusWeeks(1);
        long daysFromAnchor = ChronoUnit.DAYS.between(firstPeriodStart, referenceDate);
        long blockIndex = Math.floorDiv(daysFromAnchor, DAYS_PER_BIWEEK);
        LocalDate periodStart = firstPeriodStart.plusDays(blockIndex * DAYS_PER_BIWEEK);
        return new RecurrencePeriod(periodStart, periodStart.plusDays(DAYS_PER_BIWEEK));
    }

    private LocalDate startOfWeek(LocalDate date, DayOfWeek weekStartsOn) {
        int daysSinceWeekStart = Math.floorMod(
                date.getDayOfWeek().getValue() - weekStartsOn.getValue(),
                DAYS_PER_WEEK
        );
        return date.minusDays(daysSinceWeekStart);
    }
}
