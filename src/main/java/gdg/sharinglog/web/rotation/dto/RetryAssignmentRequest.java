package gdg.sharinglog.web.rotation.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record RetryAssignmentRequest(
        @NotNull(message = "담당 가능 멤버 조건의 출처는 필수입니다.")
        EligibilitySource eligibilitySource,

        @PositiveOrZero(message = "업무 버전은 0 이상이어야 합니다.")
        Long sourceChoreVersion
) {

    @JsonIgnore
    @AssertTrue(message = "현재 업무 조건을 사용할 때는 업무 버전이 필요합니다.")
    public boolean isSourceChoreVersionValid() {
        if (eligibilitySource == null) {
            return true;
        }
        return switch (eligibilitySource) {
            case OCCURRENCE_SNAPSHOT -> sourceChoreVersion == null;
            case CURRENT_CHORE -> sourceChoreVersion != null;
        };
    }

    public enum EligibilitySource {
        OCCURRENCE_SNAPSHOT,
        CURRENT_CHORE
    }
}
