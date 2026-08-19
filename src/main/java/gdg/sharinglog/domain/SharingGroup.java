package gdg.sharinglog.domain;

import java.time.Instant;
import java.time.DayOfWeek;
import java.time.ZoneId;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Table(name = "sharing_groups")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Entity
public class SharingGroup {

    public static final String DEFAULT_TIME_ZONE_ID = "Asia/Seoul";
    public static final DayOfWeek DEFAULT_WEEK_STARTS_ON = DayOfWeek.MONDAY;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false)
    private Long id;

    @Column(name = "public_id", nullable = false, updatable = false, unique = true, length = 36)
    private String publicId;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "address", length = 255)
    private String address;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "created_by_user_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_sharing_groups_created_by_user")
    )
    private User createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "time_zone_id", nullable = false, length = 40)
    private String timeZoneId;

    @Enumerated(EnumType.STRING)
    @Column(name = "week_starts_on", nullable = false, length = 10)
    private DayOfWeek weekStartsOn;

    public SharingGroup(String name, User createdBy) {
        this.publicId = UUID.randomUUID().toString();
        this.name = Objects.requireNonNull(name, "그룹 이름은 필수입니다.");
        this.createdBy = Objects.requireNonNull(createdBy, "그룹 생성자는 필수입니다.");
        this.createdAt = Instant.now();
        this.timeZoneId = DEFAULT_TIME_ZONE_ID;
        this.weekStartsOn = DEFAULT_WEEK_STARTS_ON;
    }

    public ZoneId timeZone() {
        return ZoneId.of(timeZoneId);
    }

    public void configureSchedulePolicy(ZoneId timeZone, DayOfWeek weekStartsOn) {
        this.timeZoneId = Objects.requireNonNull(timeZone, "그룹 시간대는 필수입니다.").getId();
        this.weekStartsOn = Objects.requireNonNull(weekStartsOn, "주 시작 요일은 필수입니다.");
    }

    public void updateName(String name) {
        this.name = Objects.requireNonNull(name, "그룹 이름은 필수입니다.");
    }

    public void updateAddress(String address) {
        this.address = address == null || address.isBlank() ? null : address.trim();
    }

    public void delete(Instant deletedAt) {
        if (this.deletedAt != null) {
            throw new IllegalStateException("이미 삭제된 그룹입니다.");
        }
        this.deletedAt = Objects.requireNonNull(deletedAt, "그룹 삭제 시각은 필수입니다.");
    }
}
