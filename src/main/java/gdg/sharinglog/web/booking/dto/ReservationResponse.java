package gdg.sharinglog.web.booking.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import gdg.sharinglog.domain.booking.ReservationStatus;

public record ReservationResponse(
        String reservationId,
        String spaceId,
        String spaceName,
        ReservationMemberResponse member,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        ReservationStatus status,
        Instant createdAt,
        Instant cancelledAt,
        long version
) {
}
