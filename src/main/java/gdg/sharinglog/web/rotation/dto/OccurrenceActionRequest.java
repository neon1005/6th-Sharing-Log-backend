package gdg.sharinglog.web.rotation.dto;

import jakarta.validation.constraints.Size;

public record OccurrenceActionRequest(
        @Size(max = 300, message = "메모는 300자 이하여야 합니다.")
        String note
) {
}
