package gdg.sharinglog.service.booking;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import gdg.sharinglog.domain.GroupMember;
import gdg.sharinglog.domain.booking.Reservation;
import gdg.sharinglog.domain.booking.ReservationStatus;
import gdg.sharinglog.domain.booking.Space;
import gdg.sharinglog.repository.booking.ReservationRepository;
import gdg.sharinglog.repository.booking.SpaceRepository;
import gdg.sharinglog.service.booking.exception.BookingAccessDeniedException;
import gdg.sharinglog.service.booking.exception.BookingConflictException;
import gdg.sharinglog.service.booking.exception.ReservationNotFoundException;
import gdg.sharinglog.service.booking.exception.SpaceNotFoundException;
import gdg.sharinglog.web.booking.dto.ReservationListResponse;
import gdg.sharinglog.web.booking.dto.ReservationMemberResponse;
import gdg.sharinglog.web.booking.dto.ReservationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReservationService {

    private final BookingAccessService accessService;
    private final SpaceRepository spaceRepository;
    private final ReservationRepository reservationRepository;

    @Transactional(readOnly = true)
    public ReservationListResponse listReservations(
            String groupPublicId,
            String spacePublicId,
            LocalDate date,
            String registrationId,
            OAuth2User principal
    ) {
        BookingActor actor = accessService.requireActiveMember(groupPublicId, registrationId, principal);
        Space space = spaceRepository.findByPublicIdAndGroup_PublicId(spacePublicId, groupPublicId)
                .orElseThrow(() -> new SpaceNotFoundException(spacePublicId));
        List<ReservationResponse> items = reservationRepository
                .findAllBySpace_IdAndDateAndStatusOrderByStartTimeAsc(
                        space.getId(),
                        date,
                        ReservationStatus.ACTIVE
                )
                .stream()
                .map(reservation -> toResponse(reservation, space, actor))
                .toList();
        return new ReservationListResponse(
                actor.group().getPublicId(),
                space.getPublicId(),
                space.getName(),
                date,
                items
        );
    }

    @Transactional
    public ReservationResponse createReservation(
            String groupPublicId,
            String spacePublicId,
            String registrationId,
            OAuth2User principal,
            LocalDate date,
            LocalTime startTime,
            LocalTime endTime
    ) {
        BookingActor actor = accessService.requireActiveMember(groupPublicId, registrationId, principal);
        Space space = spaceRepository
                .findByPublicIdAndGroupPublicIdForUpdate(spacePublicId, groupPublicId)
                .orElseThrow(() -> new SpaceNotFoundException(spacePublicId));
        if (date.isBefore(LocalDate.now(actor.group().timeZone()))) {
            throw new IllegalArgumentException("지난 날짜는 예약할 수 없습니다.");
        }

        List<Reservation> existing = reservationRepository
                .findAllBySpace_IdAndDateAndStatusOrderByStartTimeAsc(
                        space.getId(),
                        date,
                        ReservationStatus.ACTIVE
                );
        boolean overlapping = existing.stream().anyMatch(item -> item.overlaps(startTime, endTime));
        if (overlapping) {
            throw new BookingConflictException("이미 예약된 시간대와 겹칩니다.");
        }

        Reservation reservation = reservationRepository.save(
                new Reservation(space, actor.membership(), date, startTime, endTime, Instant.now())
        );
        return toResponse(reservation, space, actor);
    }

    @Transactional
    public ReservationResponse cancelReservation(
            String groupPublicId,
            String reservationPublicId,
            String registrationId,
            OAuth2User principal,
            Long expectedVersion
    ) {
        BookingActor actor = accessService.requireActiveMember(groupPublicId, registrationId, principal);
        Reservation reservation = reservationRepository
                .findByPublicIdAndSpaceGroupPublicIdForUpdate(reservationPublicId, groupPublicId)
                .orElseThrow(() -> new ReservationNotFoundException(reservationPublicId));
        if (!reservation.getMember().getId().equals(actor.membership().getId())) {
            throw new BookingAccessDeniedException("본인의 예약만 취소할 수 있습니다.");
        }
        if (expectedVersion != null && reservation.getVersion() != expectedVersion) {
            throw new BookingConflictException("예약 정보가 변경되었습니다. 다시 확인해 주세요.");
        }
        reservation.cancel(Instant.now());
        return toResponse(reservation, reservation.getSpace(), actor);
    }

    private ReservationResponse toResponse(Reservation reservation, Space space, BookingActor actor) {
        GroupMember member = reservation.getMember();
        boolean me = member.getId().equals(actor.membership().getId());
        return new ReservationResponse(
                reservation.getPublicId(),
                space.getPublicId(),
                space.getName(),
                new ReservationMemberResponse(
                        member.getPublicId(),
                        member.getUser().getEmail(),
                        member.getUser().getNickname(),
                        me
                ),
                reservation.getDate(),
                reservation.getStartTime(),
                reservation.getEndTime(),
                reservation.getStatus(),
                reservation.getCreatedAt(),
                reservation.getCancelledAt(),
                reservation.getVersion()
        );
    }
}
