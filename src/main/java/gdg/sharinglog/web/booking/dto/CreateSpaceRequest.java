package gdg.sharinglog.web.booking.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateSpaceRequest(
        @NotBlank(message = "공간 이름은 필수입니다.")
        String name
) {
}
