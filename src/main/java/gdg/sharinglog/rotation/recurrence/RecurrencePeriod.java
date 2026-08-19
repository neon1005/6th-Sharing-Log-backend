package gdg.sharinglog.rotation.recurrence;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * A recurrence period represented as the half-open date range
 * {@code [periodStart, periodEndExclusive)} in the group's time zone.
 */
public record RecurrencePeriod(LocalDate periodStart, LocalDate periodEndExclusive) {

    public RecurrencePeriod {
        Objects.requireNonNull(periodStart, "periodStart must not be null");
        Objects.requireNonNull(periodEndExclusive, "periodEndExclusive must not be null");

        if (!periodStart.isBefore(periodEndExclusive)) {
            throw new IllegalArgumentException(
                    "periodStart must be before periodEndExclusive"
            );
        }
    }

    public boolean contains(LocalDate date) {
        Objects.requireNonNull(date, "date must not be null");
        return !date.isBefore(periodStart) && date.isBefore(periodEndExclusive);
    }

    public long lengthInDays() {
        return ChronoUnit.DAYS.between(periodStart, periodEndExclusive);
    }
}
