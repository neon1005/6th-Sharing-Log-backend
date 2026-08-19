package gdg.sharinglog.web.rotation.dto;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

import com.fasterxml.jackson.annotation.JsonIgnore;
import gdg.sharinglog.domain.rotation.ChoreFrequency;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

public record ChoreScheduleRequest(
        @NotNull(message = "반복 주기는 필수입니다.")
        ChoreFrequency frequency,

        @NotNull(message = "마감 시각은 필수입니다.")
        LocalTime dueTime,

        DayOfWeek weeklyDueDay,
        LocalDate biweeklyAnchorDate
) {

    @JsonIgnore
    @AssertTrue(message = "반복 주기에 맞는 일정 필드를 설정해야 합니다.")
    public boolean isFrequencySpecificFieldsValid() {
        return frequency == null || switch (frequency) {
            case DAILY -> weeklyDueDay == null && biweeklyAnchorDate == null;
            case WEEKLY -> weeklyDueDay != null && biweeklyAnchorDate == null;
            case BIWEEKLY -> weeklyDueDay == null && biweeklyAnchorDate != null;
        };
    }
}
