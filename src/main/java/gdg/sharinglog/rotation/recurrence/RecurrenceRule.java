package gdg.sharinglog.rotation.recurrence;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Objects;

public sealed interface RecurrenceRule
        permits RecurrenceRule.Daily, RecurrenceRule.Weekly, RecurrenceRule.Biweekly {

    RecurrenceType type();

    static Daily daily() {
        return new Daily();
    }

    static Weekly weekly(DayOfWeek weekStartsOn) {
        return new Weekly(weekStartsOn);
    }

    static Biweekly biweekly(LocalDate anchor) {
        return new Biweekly(anchor);
    }

    record Daily() implements RecurrenceRule {

        @Override
        public RecurrenceType type() {
            return RecurrenceType.DAILY;
        }
    }

    record Weekly(DayOfWeek weekStartsOn) implements RecurrenceRule {

        public Weekly {
            Objects.requireNonNull(weekStartsOn, "weekStartsOn must not be null");
        }

        @Override
        public RecurrenceType type() {
            return RecurrenceType.WEEKLY;
        }
    }

    record Biweekly(LocalDate anchor) implements RecurrenceRule {

        public Biweekly {
            Objects.requireNonNull(anchor, "anchor must not be null");
        }

        @Override
        public RecurrenceType type() {
            return RecurrenceType.BIWEEKLY;
        }
    }
}
