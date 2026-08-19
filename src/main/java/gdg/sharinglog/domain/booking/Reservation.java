package gdg.sharinglog.domain.booking;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;
import java.util.UUID;

import gdg.sharinglog.domain.GroupMember;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Table(
        name = "reservations",
        indexes = @Index(
                name = "idx_reservations_space_date",
                columnList = "space_id, reservation_date, status"
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Entity
public class Reservation {

    private static final int SLOT_MINUTES = 30;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false)
    private Long id;

    @Column(name = "public_id", nullable = false, updatable = false, unique = true, length = 36)
    private String publicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "space_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_reservations_space")
    )
    private Space space;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "member_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_reservations_member")
    )
    private GroupMember member;

    @Column(name = "reservation_date", nullable = false)
    private LocalDate date;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ReservationStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    public Reservation(
            Space space,
            GroupMember member,
            LocalDate date,
            LocalTime startTime,
            LocalTime endTime,
            Instant createdAt
    ) {
        this.space = Objects.requireNonNull(space, "공간은 필수입니다.");
        this.member = Objects.requireNonNull(member, "예약자는 필수입니다.");
        this.date = Objects.requireNonNull(date, "예약 날짜는 필수입니다.");
        this.startTime = Objects.requireNonNull(startTime, "시작 시간은 필수입니다.");
        this.endTime = Objects.requireNonNull(endTime, "종료 시간은 필수입니다.");
        validateTimeRange();
        this.publicId = UUID.randomUUID().toString();
        this.status = ReservationStatus.ACTIVE;
        this.createdAt = Objects.requireNonNull(createdAt, "생성 시각은 필수입니다.");
    }

    private void validateTimeRange() {
        if (!startTime.isBefore(endTime)) {
            throw new IllegalArgumentException("시작 시간은 종료 시간보다 빨라야 합니다.");
        }
        if (startTime.getMinute() % SLOT_MINUTES != 0 || endTime.getMinute() % SLOT_MINUTES != 0
                || startTime.getSecond() != 0 || endTime.getSecond() != 0) {
            throw new IllegalArgumentException("예약 시간은 30분 단위여야 합니다.");
        }
    }

    public boolean overlaps(LocalTime otherStart, LocalTime otherEnd) {
        return startTime.isBefore(otherEnd) && endTime.isAfter(otherStart);
    }

    public void cancel(Instant cancelledAt) {
        if (status != ReservationStatus.ACTIVE) {
            throw new IllegalStateException("이미 취소된 예약입니다.");
        }
        this.status = ReservationStatus.CANCELLED;
        this.cancelledAt = Objects.requireNonNull(cancelledAt, "취소 시각은 필수입니다.");
    }
}
