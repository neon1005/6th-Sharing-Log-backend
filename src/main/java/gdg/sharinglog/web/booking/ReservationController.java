package gdg.sharinglog.web.booking;

import java.time.LocalDate;

import gdg.sharinglog.service.booking.ReservationService;
import gdg.sharinglog.web.booking.dto.CreateReservationRequest;
import gdg.sharinglog.web.booking.dto.ReservationListResponse;
import gdg.sharinglog.web.booking.dto.ReservationResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/groups/{groupId}")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    @GetMapping("/spaces/{spaceId}/reservations")
    public ReservationListResponse list(
            @PathVariable String groupId,
            @PathVariable String spaceId,
            @RequestParam LocalDate date,
            OAuth2AuthenticationToken authentication
    ) {
        return reservationService.listReservations(
                groupId,
                spaceId,
                date,
                authentication.getAuthorizedClientRegistrationId(),
                authentication.getPrincipal()
        );
    }

    @PostMapping("/spaces/{spaceId}/reservations")
    @ResponseStatus(HttpStatus.CREATED)
    public ReservationResponse create(
            @PathVariable String groupId,
            @PathVariable String spaceId,
            @Valid @RequestBody CreateReservationRequest request,
            OAuth2AuthenticationToken authentication
    ) {
        return reservationService.createReservation(
                groupId,
                spaceId,
                authentication.getAuthorizedClientRegistrationId(),
                authentication.getPrincipal(),
                request.date(),
                request.startTime(),
                request.endTime()
        );
    }

    @PostMapping("/reservations/{reservationId}/cancel")
    public ReservationResponse cancel(
            @PathVariable String groupId,
            @PathVariable String reservationId,
            @RequestHeader(name = "If-Match", required = false) Long ifMatch,
            OAuth2AuthenticationToken authentication
    ) {
        return reservationService.cancelReservation(
                groupId,
                reservationId,
                authentication.getAuthorizedClientRegistrationId(),
                authentication.getPrincipal(),
                ifMatch
        );
    }
}
