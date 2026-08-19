package gdg.sharinglog.service.rotation.occurrence;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

import gdg.sharinglog.domain.GroupMember;
import gdg.sharinglog.domain.OAuthProvider;
import gdg.sharinglog.domain.SharingGroup;
import gdg.sharinglog.domain.User;
import gdg.sharinglog.domain.rotation.Chore;
import gdg.sharinglog.domain.rotation.ChoreEligibilityMode;
import org.junit.jupiter.api.Test;

class ChoreOccurrenceScheduleResolverTest {

    private final ChoreOccurrenceScheduleResolver resolver =
            new ChoreOccurrenceScheduleResolver();

    @Test
    void resolvesDailyDueAtInGroupTimeZone() {
        Context context = context("Asia/Seoul", DayOfWeek.MONDAY);
        Chore chore = Chore.daily(
                context.group(),
                context.owner(),
                "설거지",
                ChoreEligibilityMode.ALL_ACTIVE_MEMBERS,
                LocalTime.of(22, 0),
                Instant.EPOCH
        );

        OccurrenceSchedule schedule = resolver.resolve(
                chore,
                Instant.parse("2026-07-22T16:00:00Z")
        );

        assertEquals(LocalDate.of(2026, 7, 23), schedule.periodStart());
        assertEquals(Instant.parse("2026-07-23T13:00:00Z"), schedule.dueAt());
    }

    @Test
    void resolvesWeeklyDueDayInsideConfiguredWeek() {
        Context context = context("Asia/Seoul", DayOfWeek.MONDAY);
        Chore chore = Chore.weekly(
                context.group(),
                context.owner(),
                "욕실 청소",
                ChoreEligibilityMode.ALL_ACTIVE_MEMBERS,
                DayOfWeek.SATURDAY,
                LocalTime.of(18, 0),
                Instant.EPOCH
        );

        OccurrenceSchedule schedule = resolver.resolve(
                chore,
                Instant.parse("2026-07-23T00:00:00Z")
        );

        assertEquals(LocalDate.of(2026, 7, 20), schedule.periodStart());
        assertEquals(LocalDate.of(2026, 7, 27), schedule.periodEndExclusive());
        assertEquals(Instant.parse("2026-07-25T09:00:00Z"), schedule.dueAt());
    }

    @Test
    void resolvesBiweeklyDueAtLastLocalDateOfCurrentBlock() {
        Context context = context("Asia/Seoul", DayOfWeek.MONDAY);
        Chore chore = Chore.biweekly(
                context.group(),
                context.owner(),
                "분리수거",
                ChoreEligibilityMode.ALL_ACTIVE_MEMBERS,
                LocalDate.of(2026, 7, 13),
                LocalTime.of(20, 0),
                Instant.EPOCH
        );

        OccurrenceSchedule schedule = resolver.resolve(
                chore,
                Instant.parse("2026-07-23T00:00:00Z")
        );

        assertEquals(LocalDate.of(2026, 7, 13), schedule.periodStart());
        assertEquals(LocalDate.of(2026, 7, 27), schedule.periodEndExclusive());
        assertEquals(Instant.parse("2026-07-26T11:00:00Z"), schedule.dueAt());
    }

    private Context context(String zoneId, DayOfWeek weekStart) {
        User user = User.builder()
                .provider(OAuthProvider.GOOGLE)
                .providerUserId("schedule-" + zoneId)
                .build();
        SharingGroup group = new SharingGroup("우리 집", user);
        group.configureSchedulePolicy(ZoneId.of(zoneId), weekStart);
        return new Context(group, GroupMember.owner(group, user));
    }

    private record Context(SharingGroup group, GroupMember owner) {
    }
}
