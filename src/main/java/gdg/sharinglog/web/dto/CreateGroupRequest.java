package gdg.sharinglog.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateGroupRequest(
        @NotBlank(message = "그룹 이름은 필수입니다.")
        @Size(max = 50, message = "그룹 이름은 50자 이하여야 합니다.")
        String name,
        @Size(max = 255, message = "주소는 255자 이하여야 합니다.")
        String address
) {
}
