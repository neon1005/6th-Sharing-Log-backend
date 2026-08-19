package gdg.sharinglog.web.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateGroupRequest(
        @Pattern(regexp = "(?s).*\\S.*", message = "그룹 이름은 공백일 수 없습니다.")
        @Size(max = 50, message = "그룹 이름은 50자 이하여야 합니다.")
        String name,
        @Size(max = 255, message = "주소는 255자 이하여야 합니다.")
        String address
) {

    @AssertTrue(message = "그룹 이름 또는 주소 중 하나 이상을 입력해 주세요.")
    public boolean isAnyFieldPresent() {
        return name != null || address != null;
    }
}
