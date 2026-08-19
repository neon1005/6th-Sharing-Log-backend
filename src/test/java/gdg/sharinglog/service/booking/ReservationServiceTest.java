package gdg.sharinglog.service.booking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import gdg.sharinglog.domain.GroupMember;
import gdg.sharinglog.domain.SharingGroup;
import gdg.sharinglog.domain.User;
import gdg.sharinglog.domain.booking.Reservation;
import gdg.sharinglog.domain.booking.ReservationStatus;
import gdg.sharinglog.domain.booking.Space;
import gdg.sharinglog.repository.booking.ReservationRepository;
import gdg.sharinglog.repository.booking.SpaceRepository;
import gdg.sharinglog.service.booking.exception.BookingAccessDeniedException;
import gdg.sharinglog.service.booking.exception.BookingConflictException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.core.user.OAuth2User;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @Mock
    BookingAccessService accessService;

    @Mock
    SpaceRepository spaceRepository;

    @Mock
    ReservationRepository reservationRepository;

    @InjectMocks
    ReservationService service;

    @Test
    void listReservationsReturnsMemberNickname() {
        String groupPublicId = "grp-1";
        String spacePublicId = "space-1";
        LocalDate date = LocalDate.of(2026, 8, 18);
        OAuth2User principal = mock(OAuth2User.class);
        SharingGroup group = mock(SharingGroup.class);
        GroupMember actorMembership = mock(GroupMember.class);
        BookingActor actor = new BookingActor(group, actorMembership);
        Space space = mock(Space.class);
        Reservation reservation = mock(Reservation.class);
        GroupMember reservationMember = mock(GroupMember.class);
        User reservationUser = mock(User.class);

        when(accessService.requireActiveMember(groupPublicId, "google", principal)).thenReturn(actor);
        when(spaceRepository.findByPublicIdAndGroup_PublicId(spacePublicId, groupPublicId))
                .thenReturn(Optional.of(space));
        when(space.getId()).thenReturn(1L);
        when(reservationRepository.findAllBySpace_IdAndDateAndStatusOrderByStartTimeAsc(
                1L, date, ReservationStatus.ACTIVE
        )).thenReturn(List.of(reservation));
        when(reservation.getMember()).thenReturn(reservationMember);
        when(reservationMember.getId()).thenReturn(2L);
        when(actorMembership.getId()).thenReturn(1L);
        when(reservationMember.getUser()).thenReturn(reservationUser);
        when(reservationUser.getNickname()).thenReturn("공간지킴이");

        var response = service.listReservations(
                groupPublicId, spacePublicId, date, "google", principal
        );

        assertEquals("공간지킴이", response.items().getFirst().member().nickname());
    }

    @Test
    void createReservationRejectsOverlap() {
        String groupPublicId = "grp-1";
        String spacePublicId = "space-1";
        OAuth2User principal = mock(OAuth2User.class);
        SharingGroup group = mock(SharingGroup.class);
        GroupMember membership = mock(GroupMember.class);
        BookingActor actor = new BookingActor(group, membership);
        Space space = mock(Space.class);
        Reservation existing = mock(Reservation.class);
        LocalDate date = LocalDate.now(SEOUL).plusDays(1);
        LocalTime start = LocalTime.of(19, 0);
        LocalTime end = LocalTime.of(19, 30);

        when(accessService.requireActiveMember(groupPublicId, "google", principal)).thenReturn(actor);
        when(group.timeZone()).thenReturn(SEOUL);
        when(spaceRepository.findByPublicIdAndGroupPublicIdForUpdate(spacePublicId, groupPublicId))
                .thenReturn(Optional.of(space));
        when(space.getId()).thenReturn(1L);
        when(reservationRepository.findAllBySpace_IdAndDateAndStatusOrderByStartTimeAsc(
                1L, date, ReservationStatus.ACTIVE
        )).thenReturn(List.of(existing));
        when(existing.overlaps(start, end)).thenReturn(true);

        assertThrows(BookingConflictException.class, () ->
                service.createReservation(groupPublicId, spacePublicId, "google", principal, date, start, end));
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void createReservationRejectsPastDate() {
        String groupPublicId = "grp-1";
        String spacePublicId = "space-1";
        OAuth2User principal = mock(OAuth2User.class);
        SharingGroup group = mock(SharingGroup.class);
        GroupMember membership = mock(GroupMember.class);
        BookingActor actor = new BookingActor(group, membership);
        Space space = mock(Space.class);
        LocalDate yesterday = LocalDate.now(SEOUL).minusDays(1);

        when(accessService.requireActiveMember(groupPublicId, "google", principal)).thenReturn(actor);
        when(group.timeZone()).thenReturn(SEOUL);
        when(spaceRepository.findByPublicIdAndGroupPublicIdForUpdate(spacePublicId, groupPublicId))
                .thenReturn(Optional.of(space));

        assertThrows(IllegalArgumentException.class, () -> service.createReservation(
                groupPublicId, spacePublicId, "google", principal,
                yesterday, LocalTime.of(19, 0), LocalTime.of(19, 30)
        ));
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void cancelReservationRejectsNonOwner() {
        String groupPublicId = "grp-1";
        String reservationPublicId = "res-1";
        OAuth2User principal = mock(OAuth2User.class);
        SharingGroup group = mock(SharingGroup.class);
        GroupMember membership = mock(GroupMember.class);
        BookingActor actor = new BookingActor(group, membership);
        Reservation reservation = mock(Reservation.class);
        GroupMember owner = mock(GroupMember.class);

        when(accessService.requireActiveMember(groupPublicId, "google", principal)).thenReturn(actor);
        when(reservationRepository.findByPublicIdAndSpaceGroupPublicIdForUpdate(reservationPublicId, groupPublicId))
                .thenReturn(Optional.of(reservation));
        when(reservation.getMember()).thenReturn(owner);
        when(owner.getId()).thenReturn(99L);
        when(membership.getId()).thenReturn(1L);

        assertThrows(BookingAccessDeniedException.class, () ->
                service.cancelReservation(groupPublicId, reservationPublicId, "google", principal, null));
        verify(reservation, never()).cancel(any());
    }

    @Test
    void cancelReservationRejectsVersionMismatch() {
        String groupPublicId = "grp-1";
        String reservationPublicId = "res-1";
        OAuth2User principal = mock(OAuth2User.class);
        SharingGroup group = mock(SharingGroup.class);
        GroupMember membership = mock(GroupMember.class);
        BookingActor actor = new BookingActor(group, membership);
        Reservation reservation = mock(Reservation.class);

        when(accessService.requireActiveMember(groupPublicId, "google", principal)).thenReturn(actor);
        when(reservationRepository.findByPublicIdAndSpaceGroupPublicIdForUpdate(reservationPublicId, groupPublicId))
                .thenReturn(Optional.of(reservation));
        when(reservation.getMember()).thenReturn(membership);
        when(membership.getId()).thenReturn(1L);
        when(reservation.getVersion()).thenReturn(2L);

        assertThrows(BookingConflictException.class, () ->
                service.cancelReservation(groupPublicId, reservationPublicId, "google", principal, 1L));
        verify(reservation, never()).cancel(any());
    }

    @Test
    void cancelReservationSucceedsForOwnerWithMatchingVersion() {
        String groupPublicId = "grp-1";
        String reservationPublicId = "res-1";
        OAuth2User principal = mock(OAuth2User.class);
        SharingGroup group = mock(SharingGroup.class);
        GroupMember membership = mock(GroupMember.class);
        BookingActor actor = new BookingActor(group, membership);
        Reservation reservation = mock(Reservation.class);
        Space space = mock(Space.class);

        when(accessService.requireActiveMember(groupPublicId, "google", principal)).thenReturn(actor);
        when(reservationRepository.findByPublicIdAndSpaceGroupPublicIdForUpdate(reservationPublicId, groupPublicId))
                .thenReturn(Optional.of(reservation));
        when(reservation.getMember()).thenReturn(membership);
        when(membership.getId()).thenReturn(1L);
        when(reservation.getVersion()).thenReturn(2L);
        when(reservation.getSpace()).thenReturn(space);
        when(membership.getUser()).thenReturn(mock(User.class));

        service.cancelReservation(groupPublicId, reservationPublicId, "google", principal, 2L);

        verify(reservation).cancel(any());
    }
}
