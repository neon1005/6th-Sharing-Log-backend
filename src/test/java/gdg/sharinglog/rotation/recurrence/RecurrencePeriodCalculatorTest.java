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
    void biweeklyAlignsFourteenDayBlocksAroundTheFirstDueDate() {
        RecurrenceRule rule = RecurrenceRule.biweekly(
                LocalDate.of(2026, 8, 24),
                DayOfWeek.MONDAY
        );

        assertThat(calculator.calculate(LocalDate.of(2026, 8, 23), rule))
                .isEqualTo(period("2026-08-17", "2026-08-31"));
        assertThat(calculator.calculate(LocalDate.of(2026, 8, 30), rule))
                .isEqualTo(period("2026-08-17", "2026-08-31"));
        assertThat(calculator.calculate(LocalDate.of(2026, 8, 31), rule))
                .isEqualTo(period("2026-08-31", "2026-09-14"));
    }

    @Test
    void biweeklyUsesFloorDivisionBeforeTheFirstPeriod() {
        RecurrenceRule rule = RecurrenceRule.biweekly(
                LocalDate.of(2026, 8, 24),
                DayOfWeek.MONDAY
        );

        RecurrencePeriod oneDayBeforeFirstPeriod = calculator.calculate(
                LocalDate.of(2026, 8, 16),
                rule
        );
        RecurrencePeriod fifteenDaysBeforeFirstPeriod = calculator.calculate(
                LocalDate.of(2026, 8, 2),
                rule
        );

        assertThat(oneDayBeforeFirstPeriod)
                .isEqualTo(period("2026-08-03", "2026-08-17"));
        assertThat(fifteenDaysBeforeFirstPeriod)
                .isEqualTo(period("2026-07-20", "2026-08-03"));
    }

    @Test
    void everyReturnedPeriodContainsItsReferenceDateAndExcludesItsEnd() {
        LocalDate referenceDate = LocalDate.of(2026, 12, 31);
        RecurrencePeriod result = calculator.calculate(
                referenceDate,
                RecurrenceRule.biweekly(
                        LocalDate.of(2026, 1, 1),
                        DayOfWeek.MONDAY
                )
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
