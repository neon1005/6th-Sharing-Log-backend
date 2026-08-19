package gdg.sharinglog.domain.rotation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import gdg.sharinglog.domain.GroupMember;
import gdg.sharinglog.domain.OAuthProvider;
import gdg.sharinglog.domain.SharingGroup;
import gdg.sharinglog.domain.User;
import org.junit.jupiter.api.Test;

class ChoreScheduleValidationTest {

    @Test
    void createsDailyWeeklyAndBiweeklySchedules() {
        User owner = user("schedule-owner");
        SharingGroup group = new SharingGroup("우리 집", owner);
        GroupMember membership = GroupMember.owner(group, owner);
        Instant now = Instant.parse("2026-07-23T00:00:00Z");

        Chore daily = Chore.daily(
                group, membership, "설거지", ChoreEligibilityMode.ALL_ACTIVE_MEMBERS,
                LocalTime.of(22, 0), now
        );
        Chore weekly = Chore.weekly(
                group, membership, "욕실 청소", ChoreEligibilityMode.SELECTED_MEMBERS,
                DayOfWeek.SATURDAY, LocalTime.of(18, 0), now
        );
        Chore biweekly = Chore.biweekly(
                group, membership, "분리수거", ChoreEligibilityMode.ALL_ACTIVE_MEMBERS,
                LocalDate.of(2026, 7, 20), LocalTime.of(20, 0), now
        );

        assertEquals(ChoreFrequency.DAILY, daily.getFrequency());
        assertEquals(DayOfWeek.SATURDAY, weekly.getWeeklyDueDay());
        assertEquals(LocalDate.of(2026, 7, 20), biweekly.getBiweeklyAnchorDate());
    }

    @Test
    void rejectsBiweeklyAnchorThatDoesNotMatchGroupWeekStart() {
        User owner = user("bad-anchor-owner");
        SharingGroup group = new SharingGroup("우리 집", owner);
        GroupMember membership = GroupMember.owner(group, owner);

        assertThrows(
                IllegalArgumentException.class,
                () -> Chore.biweekly(
                        group,
                        membership,
                        "분리수거",
                        ChoreEligibilityMode.ALL_ACTIVE_MEMBERS,
                        LocalDate.of(2026, 7, 21),
                        LocalTime.of(20, 0),
                        Instant.parse("2026-07-23T00:00:00Z")
                )
        );
    }

    @Test
    void selectedEligibilityRejectsMemberFromAnotherGroup() {
        User owner = user("first-owner");
        SharingGroup firstGroup = new SharingGroup("첫 집", owner);
        GroupMember creator = GroupMember.owner(firstGroup, owner);
        Chore chore = Chore.daily(
                firstGroup,
                creator,
                "설거지",
                ChoreEligibilityMode.SELECTED_MEMBERS,
                LocalTime.of(22, 0),
                Instant.parse("2026-07-23T00:00:00Z")
        );
        User other = user("other");
        GroupMember otherMembership = GroupMember.owner(new SharingGroup("다른 집", other), other);

        assertThrows(
                IllegalArgumentException.class,
                () -> new ChoreEligibleMember(chore, otherMembership)
        );
    }

    @Test
    void renamesAndReschedulesWithFrequencySpecificFields() {
        User owner = user("update-owner");
        SharingGroup group = new SharingGroup("우리 집", owner);
        GroupMember membership = GroupMember.owner(group, owner);
        Chore chore = Chore.daily(
                group,
                membership,
                "설거지",
                ChoreEligibilityMode.ALL_ACTIVE_MEMBERS,
                LocalTime.of(22, 0),
                Instant.parse("2026-07-23T00:00:00Z")
        );

        chore.rename("  공용 욕실 청소  ");
        boolean changed = chore.reschedule(
                ChoreFrequency.WEEKLY,
                LocalTime.of(19, 30),
                DayOfWeek.SATURDAY,
                null
        );

        assertEquals("공용 욕실 청소", chore.getName());
        assertEquals(ChoreFrequency.WEEKLY, chore.getFrequency());
        assertEquals(LocalTime.of(19, 30), chore.getDueTime());
        assertEquals(DayOfWeek.SATURDAY, chore.getWeeklyDueDay());
        assertNull(chore.getBiweeklyAnchorDate());
        assertTrue(changed);
        assertEquals(1L, chore.getScheduleRevision());
        assertFalse(chore.reschedule(
                ChoreFrequency.WEEKLY,
                LocalTime.of(19, 30),
                DayOfWeek.SATURDAY,
                null
        ));
        assertEquals(1L, chore.getScheduleRevision());
    }

    private User user(String providerUserId) {
        return User.builder()
                .provider(OAuthProvider.GOOGLE)
                .providerUserId(providerUserId)
                .build();
    }
}
