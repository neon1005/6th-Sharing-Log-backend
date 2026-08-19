package gdg.sharinglog.service.rotation.occurrence;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

public record OccurrenceSchedule(
        LocalDate periodStart,
        LocalDate periodEndExclusive,
        Instant dueAt
) {

    public OccurrenceSchedule {
        Objects.requireNonNull(periodStart, "기간 시작일은 필수입니다.");
        Objects.requireNonNull(periodEndExclusive, "기간 종료일은 필수입니다.");
        Objects.requireNonNull(dueAt, "마감 시각은 필수입니다.");
        if (!periodEndExclusive.isAfter(periodStart)) {
            throw new IllegalArgumentException("기간 종료일은 시작일보다 뒤여야 합니다.");
        }
    }
}
