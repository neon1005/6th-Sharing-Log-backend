package gdg.sharinglog.service.rotation.occurrence;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

import gdg.sharinglog.domain.rotation.Chore;
import gdg.sharinglog.rotation.recurrence.RecurrencePeriod;
import gdg.sharinglog.rotation.recurrence.RecurrencePeriodCalculator;
import gdg.sharinglog.rotation.recurrence.RecurrenceRule;
import org.springframework.stereotype.Component;

@Component
public class ChoreOccurrenceScheduleResolver {

    private final RecurrencePeriodCalculator periodCalculator = new RecurrencePeriodCalculator();

    public OccurrenceSchedule resolve(Chore chore, java.time.Instant referenceInstant) {
        Objects.requireNonNull(chore, "업무는 필수입니다.");
        Objects.requireNonNull(referenceInstant, "기준 시각은 필수입니다.");

        ZoneId groupZone = chore.getGroup().timeZone();
        RecurrencePeriod period = periodCalculator.calculate(
                referenceInstant,
                groupZone,
                ruleFor(chore)
        );
        LocalDate dueDate = dueDate(chore, period);

        return new OccurrenceSchedule(
                period.periodStart(),
                period.periodEndExclusive(),
                dueDate.atTime(chore.getDueTime()).atZone(groupZone).toInstant()
        );
    }

    private RecurrenceRule ruleFor(Chore chore) {
        return switch (chore.getFrequency()) {
            case DAILY -> RecurrenceRule.daily();
            case WEEKLY -> RecurrenceRule.weekly(chore.getGroup().getWeekStartsOn());
            case BIWEEKLY -> RecurrenceRule.biweekly(
                    chore.getBiweeklyDueDate(),
                    chore.getGroup().getWeekStartsOn()
            );
        };
    }

    private LocalDate dueDate(Chore chore, RecurrencePeriod period) {
        return switch (chore.getFrequency()) {
            case DAILY -> period.periodStart();
            case BIWEEKLY -> {
                long dueOffset = Math.floorMod(
                        ChronoUnit.DAYS.between(
                                period.periodStart(),
                                chore.getBiweeklyDueDate()
                        ),
                        14L
                );
                yield period.periodStart().plusDays(dueOffset);
            }
            case WEEKLY -> {
                int dayOffset = Math.floorMod(
                        chore.getWeeklyDueDay().getValue()
                                - chore.getGroup().getWeekStartsOn().getValue(),
                        7
                );
                yield period.periodStart().plusDays(dayOffset);
            }
        };
    }
}
