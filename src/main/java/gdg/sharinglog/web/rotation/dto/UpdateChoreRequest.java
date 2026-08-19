package gdg.sharinglog.web.rotation.dto;

import java.util.HashSet;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import gdg.sharinglog.domain.rotation.ChoreEligibilityMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateChoreRequest(
        @Size(max = 100, message = "업무명은 100자 이하여야 합니다.")
        String name,

        @Valid
        ChoreScheduleRequest schedule,

        @Valid
        EligibilityRequest eligibility
) {

    private static final String UUID_PATTERN =
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-"
                    + "[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";

    @JsonIgnore
    @AssertTrue(message = "업무명, 일정 또는 가능 멤버 중 하나 이상을 변경해야 합니다.")
    public boolean isChangePresent() {
        return name != null || schedule != null || eligibility != null;
    }

    @JsonIgnore
    @AssertTrue(message = "업무명은 공백일 수 없습니다.")
    public boolean isNameValid() {
        return name == null || !name.trim().isEmpty();
    }

    public record EligibilityRequest(
            @NotNull(message = "업무 가능 멤버 모드는 필수입니다.")
            ChoreEligibilityMode mode,

            @NotNull(message = "업무 가능 멤버 목록은 필수입니다.")
            List<
                    @NotBlank(message = "멤버십 ID는 비어 있을 수 없습니다.")
                    @Pattern(regexp = UUID_PATTERN, message = "멤버십 ID는 UUID 형식이어야 합니다.")
                    String
                    > membershipIds
    ) {

        @JsonIgnore
        @AssertTrue(message = "선택 멤버 모드에는 중복되지 않은 멤버가 한 명 이상 필요합니다.")
        public boolean isMembershipSelectionValid() {
            if (mode == null || membershipIds == null) {
                return true;
            }
            if (mode == ChoreEligibilityMode.ALL_ACTIVE_MEMBERS) {
                return membershipIds.isEmpty();
            }
            return !membershipIds.isEmpty()
                    && new HashSet<>(membershipIds).size() == membershipIds.size();
        }
    }
}
