package gdg.sharinglog.web.push.dto;

import jakarta.validation.constraints.NotBlank;

public record SubscribePushRequest(
        @NotBlank(message = "endpoint는 필수입니다.")
        String endpoint,
        @NotBlank(message = "p256dh 키는 필수입니다.")
        String p256dh,
        @NotBlank(message = "auth 키는 필수입니다.")
        String auth
) {
}
