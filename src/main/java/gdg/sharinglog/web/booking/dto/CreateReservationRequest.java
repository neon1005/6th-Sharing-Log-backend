package gdg.sharinglog.web.booking.dto;

import java.time.LocalDate;
import java.time.LocalTime;

import jakarta.validation.constraints.NotNull;

public record CreateReservationRequest(
        @NotNull(message = "예약 날짜는 필수입니다.")
        LocalDate date,
        @NotNull(message = "시작 시간은 필수입니다.")
        LocalTime startTime,
        @NotNull(message = "종료 시간은 필수입니다.")
        LocalTime endTime
) {
}
