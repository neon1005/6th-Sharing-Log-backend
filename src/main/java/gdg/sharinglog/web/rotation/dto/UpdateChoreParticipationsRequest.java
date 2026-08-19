package gdg.sharinglog.web.rotation.dto;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;
import gdg.sharinglog.service.rotation.api.member.ChoreParticipationApplicationScope;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record UpdateChoreParticipationsRequest(
        @NotNull(message = "추가할 업무 ID 목록은 필수입니다.")
        @Size(max = 1000, message = "한 번에 추가할 수 있는 업무는 1000개 이하입니다.")
        List<
                @NotBlank(message = "업무 ID는 비어 있을 수 없습니다.")
                @Pattern(regexp = UUID_PATTERN, message = "업무 ID는 UUID 형식이어야 합니다.")
                String
                > addChoreIds,

        @NotNull(message = "제외할 업무 ID 목록은 필수입니다.")
        @Size(max = 1000, message = "한 번에 제외할 수 있는 업무는 1000개 이하입니다.")
        List<
                @NotBlank(message = "업무 ID는 비어 있을 수 없습니다.")
                @Pattern(regexp = UUID_PATTERN, message = "업무 ID는 UUID 형식이어야 합니다.")
                String
                > removeChoreIds,

        @NotNull(message = "변경 적용 범위는 필수입니다.")
        ChoreParticipationApplicationScope applicationScope,

        @NotNull(message = "업무별 예상 버전은 필수입니다.")
        @Size(max = 2000, message = "업무별 예상 버전은 2000개 이하입니다.")
        Map<
                @NotBlank(message = "업무 ID는 비어 있을 수 없습니다.")
                @Pattern(regexp = UUID_PATTERN, message = "업무 ID는 UUID 형식이어야 합니다.")
                String,
                @NotNull(message = "업무 예상 버전은 필수입니다.")
                @PositiveOrZero(message = "업무 예상 버전은 0 이상이어야 합니다.")
                Long
                > expectedVersions
) {

    private static final String UUID_PATTERN =
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-"
                    + "[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";

    @JsonIgnore
    @AssertTrue(message = "추가 또는 제외할 업무가 하나 이상 필요하며 같은 업무를 중복 지정할 수 없습니다.")
    public boolean isChangeSetValid() {
        if (addChoreIds == null || removeChoreIds == null) {
            return true;
        }
        Set<String> addIds = new HashSet<>(addChoreIds);
        Set<String> removeIds = new HashSet<>(removeChoreIds);
        if (addIds.size() != addChoreIds.size()
                || removeIds.size() != removeChoreIds.size()
                || addIds.isEmpty() && removeIds.isEmpty()) {
            return false;
        }
        addIds.retainAll(removeIds);
        return addIds.isEmpty();
    }

    @JsonIgnore
    @AssertTrue(message = "업무별 예상 버전은 변경 대상 업무와 정확히 일치해야 합니다.")
    public boolean isExpectedVersionSetValid() {
        if (addChoreIds == null || removeChoreIds == null || expectedVersions == null) {
            return true;
        }
        Set<String> affectedIds = new HashSet<>(addChoreIds);
        affectedIds.addAll(removeChoreIds);
        return affectedIds.equals(expectedVersions.keySet());
    }
}
