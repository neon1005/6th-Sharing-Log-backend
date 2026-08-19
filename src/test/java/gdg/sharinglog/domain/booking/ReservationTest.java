package gdg.sharinglog.domain.booking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import gdg.sharinglog.domain.GroupMember;
import gdg.sharinglog.domain.SharingGroup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReservationTest {

    private Space space;
    private GroupMember member;

    @BeforeEach
    void setUp() {
        SharingGroup group = mock(SharingGroup.class);
        space = new Space(group, "세탁실", Instant.parse("2026-08-01T00:00:00Z"));
        member = mock(GroupMember.class);
    }

    @Test
    void rejectsStartNotBeforeEnd() {
        assertThrows(IllegalArgumentException.class, () -> new Reservation(
                space, member, LocalDate.of(2026, 8, 2),
                LocalTime.of(10, 0), LocalTime.of(10, 0), Instant.now()
        ));
    }

    @Test
    void rejectsNonThirtyMinuteAlignedTimes() {
        assertThrows(IllegalArgumentException.class, () -> new Reservation(
                space, member, LocalDate.of(2026, 8, 2),
                LocalTime.of(10, 15), LocalTime.of(10, 45), Instant.now()
        ));
    }

    @Test
    void createsValidReservation() {
        Reservation reservation = new Reservation(
                space, member, LocalDate.of(2026, 8, 2),
                LocalTime.of(19, 0), LocalTime.of(19, 30), Instant.now()
        );
        assertEquals(ReservationStatus.ACTIVE, reservation.getStatus());
    }

    @Test
    void overlapsDetectsOverlappingRange() {
        Reservation reservation = new Reservation(
                space, member, LocalDate.of(2026, 8, 2),
                LocalTime.of(19, 0), LocalTime.of(20, 0), Instant.now()
        );
        assertTrue(reservation.overlaps(LocalTime.of(19, 30), LocalTime.of(20, 30)));
        assertFalse(reservation.overlaps(LocalTime.of(20, 0), LocalTime.of(20, 30)));
    }

    @Test
    void cancellingTwiceThrows() {
        Reservation reservation = new Reservation(
                space, member, LocalDate.of(2026, 8, 2),
                LocalTime.of(19, 0), LocalTime.of(19, 30), Instant.now()
        );
        reservation.cancel(Instant.now());
        assertThrows(IllegalStateException.class, () -> reservation.cancel(Instant.now()));
    }
}
