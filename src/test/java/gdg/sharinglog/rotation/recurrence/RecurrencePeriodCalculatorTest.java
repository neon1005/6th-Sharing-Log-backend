package gdg.sharinglog.rotation.recurrence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class RecurrencePeriodCalculatorTest {

    private final RecurrencePeriodCalculator calculator = new RecurrencePeriodCalculator();

    @Test
    void dailyUsesTheGroupLocalDateAtTheSeoulMidnightBoundary() {
        Instant seoulMidnight = Instant.parse("2026-12-31T15:00:00Z");

        RecurrencePeriod seoulPeriod = calculator.calculate(
                seoulMidnight,
                ZoneId.of("Asia/Seoul"),
                RecurrenceRule.daily()
        );
        RecurrencePeriod utcPeriod = calculator.calculate(
                seoulMidnight,
                ZoneOffset.UTC,
                RecurrenceRule.daily()
        );

        assertThat(seoulPeriod).isEqualTo(period("2027-01-01", "2027-01-02"));
        assertThat(utcPeriod).isEqualTo(period("2026-12-31", "2027-01-01"));
    }

    @Test
    void weeklyUsesTheConfiguredWeekStartDay() {
        LocalDate wednesday = LocalDate.of(2026, 7, 22);

        RecurrencePeriod mondayStart = calculator.calculate(
                wednesday,
                RecurrenceRule.weekly(DayOfWeek.MONDAY)
        );
        RecurrencePeriod sundayStart = calculator.calculate(
                wednesday,
                RecurrenceRule.weekly(DayOfWeek.SUNDAY)
        );

        assertThat(mondayStart).isEqualTo(period("2026-07-20", "2026-07-27"));
        assertThat(sundayStart).isEqualTo(period("2026-07-19", "2026-07-26"));
    }

    @Test
    void weeklyPeriodCanCrossTheYearBoundary() {
        RecurrencePeriod result = calculator.calculate(
                LocalDate.of(2027, 1, 1),
                RecurrenceRule.weekly(DayOfWeek.MONDAY)
        );

        assertThat(result).isEqualTo(period("2026-12-28", "2027-01-04"));
        assertThat(result.lengthInDays()).isEqualTo(7);
    }

    @Test
    void biweeklyStartsANewBlockAtTheAnchorAndEveryFourteenDays() {
        RecurrenceRule rule = RecurrenceRule.biweekly(LocalDate.of(2026, 1, 15));

        assertThat(calculator.calculate(LocalDate.of(2026, 1, 15), rule))
                .isEqualTo(period("2026-01-15", "2026-01-29"));
        assertThat(calculator.calculate(LocalDate.of(2026, 1, 28), rule))
                .isEqualTo(period("2026-01-15", "2026-01-29"));
        assertThat(calculator.calculate(LocalDate.of(2026, 1, 29), rule))
                .isEqualTo(period("2026-01-29", "2026-02-12"));
    }

    @Test
    void biweeklyUsesFloorDivisionBeforeTheAnchor() {
        RecurrenceRule rule = RecurrenceRule.biweekly(LocalDate.of(2026, 1, 15));

        RecurrencePeriod oneDayBeforeAnchor = calculator.calculate(
                LocalDate.of(2026, 1, 14),
                rule
        );
        RecurrencePeriod fifteenDaysBeforeAnchor = calculator.calculate(
                LocalDate.of(2025, 12, 31),
                rule
        );

        assertThat(oneDayBeforeAnchor).isEqualTo(period("2026-01-01", "2026-01-15"));
        assertThat(fifteenDaysBeforeAnchor)
                .isEqualTo(period("2025-12-18", "2026-01-01"));
    }

    @Test
    void everyReturnedPeriodContainsItsReferenceDateAndExcludesItsEnd() {
        LocalDate referenceDate = LocalDate.of(2026, 12, 31);
        RecurrencePeriod result = calculator.calculate(
                referenceDate,
                RecurrenceRule.biweekly(LocalDate.of(2026, 1, 1))
        );

        assertThat(result.contains(referenceDate)).isTrue();
        assertThat(result.contains(result.periodStart())).isTrue();
        assertThat(result.contains(result.periodEndExclusive())).isFalse();
        assertThat(result.lengthInDays()).isEqualTo(14);
    }

    private static RecurrencePeriod period(String start, String endExclusive) {
        return new RecurrencePeriod(LocalDate.parse(start), LocalDate.parse(endExclusive));
    }
}
