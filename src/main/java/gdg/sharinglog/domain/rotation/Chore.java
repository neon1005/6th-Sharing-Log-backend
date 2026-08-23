package gdg.sharinglog.domain.rotation;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;
import java.util.UUID;

import gdg.sharinglog.domain.GroupMember;
import gdg.sharinglog.domain.SharingGroup;
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
        name = "chores",
        indexes = {
                @Index(name = "idx_chores_group_active", columnList = "group_id, active"),
                @Index(name = "idx_chores_public_id", columnList = "public_id", unique = true)
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Entity
public class Chore {

    public static final LocalDate MAX_BIWEEKLY_DUE_DATE = LocalDate.of(9999, 12, 24);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false)
    private Long id;

    @Column(name = "public_id", nullable = false, updatable = false, unique = true, length = 36)
    private String publicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "group_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_chores_group")
    )
    private SharingGroup group;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "created_by_membership_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_chores_created_by_membership")
    )
    private GroupMember createdBy;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "frequency", nullable = false, length = 20)
    private ChoreFrequency frequency;

    @Enumerated(EnumType.STRING)
    @Column(name = "eligibility_mode", nullable = false, length = 30)
    private ChoreEligibilityMode eligibilityMode;

    @Column(name = "due_time", nullable = false)
    private LocalTime dueTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "weekly_due_day", length = 10)
    private DayOfWeek weeklyDueDay;

    @Column(name = "biweekly_due_date")
    private LocalDate biweeklyDueDate;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "eligibility_revision", nullable = false)
    private long eligibilityRevision;

    @Column(name = "schedule_revision", nullable = false)
    private long scheduleRevision;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    private Chore(
            SharingGroup group,
            GroupMember createdBy,
            String name,
            ChoreFrequency frequency,
            ChoreEligibilityMode eligibilityMode,
            LocalTime dueTime,
            DayOfWeek weeklyDueDay,
            LocalDate biweeklyDueDate,
            Instant createdAt
    ) {
        this.group = Objects.requireNonNull(group, "그룹은 필수입니다.");
        this.createdBy = requireMembershipOfGroup(createdBy, group);
        this.name = normalizeName(name);
        this.frequency = Objects.requireNonNull(frequency, "반복 주기는 필수입니다.");
        this.eligibilityMode = Objects.requireNonNull(eligibilityMode, "가능 멤버 방식은 필수입니다.");
        this.dueTime = Objects.requireNonNull(dueTime, "마감 시각은 필수입니다.");
        this.weeklyDueDay = weeklyDueDay;
        this.biweeklyDueDate = biweeklyDueDate;
        this.createdAt = Objects.requireNonNull(createdAt, "생성 시각은 필수입니다.");
        this.publicId = UUID.randomUUID().toString();
        this.active = true;
        this.eligibilityRevision = 0L;
        this.scheduleRevision = 0L;
        validateSchedule();
    }

    public static Chore daily(
            SharingGroup group,
            GroupMember createdBy,
            String name,
            ChoreEligibilityMode eligibilityMode,
            LocalTime dueTime,
            Instant createdAt
    ) {
        return new Chore(
                group, createdBy, name, ChoreFrequency.DAILY, eligibilityMode,
                dueTime, null, null, createdAt
        );
    }

    public static Chore weekly(
            SharingGroup group,
            GroupMember createdBy,
            String name,
            ChoreEligibilityMode eligibilityMode,
            DayOfWeek dueDay,
            LocalTime dueTime,
            Instant createdAt
    ) {
        return new Chore(
                group, createdBy, name, ChoreFrequency.WEEKLY, eligibilityMode,
                dueTime, Objects.requireNonNull(dueDay, "주간 마감 요일은 필수입니다."), null, createdAt
        );
    }

    public static Chore biweekly(
            SharingGroup group,
            GroupMember createdBy,
            String name,
            ChoreEligibilityMode eligibilityMode,
            LocalDate dueDate,
            LocalTime dueTime,
            Instant createdAt
    ) {
        return new Chore(
                group, createdBy, name, ChoreFrequency.BIWEEKLY, eligibilityMode,
                dueTime, null, Objects.requireNonNull(dueDate, "격주 마감일은 필수입니다."), createdAt
        );
    }

    public void deactivate() {
        this.active = false;
    }

    public void activate() {
        this.active = true;
    }

    public void rename(String name) {
        this.name = normalizeName(name);
    }

    public boolean changeEligibilityMode(ChoreEligibilityMode eligibilityMode) {
        ChoreEligibilityMode requiredMode = Objects.requireNonNull(
                eligibilityMode,
                "가능 멤버 방식은 필수입니다."
        );
        if (this.eligibilityMode == requiredMode) {
            return false;
        }
        this.eligibilityMode = requiredMode;
        recordEnrollmentChange();
        return true;
    }

    public boolean reschedule(
            ChoreFrequency frequency,
            LocalTime dueTime,
            DayOfWeek weeklyDueDay,
            LocalDate biweeklyDueDate
    ) {
        ChoreFrequency requiredFrequency =
                Objects.requireNonNull(frequency, "반복 주기는 필수입니다.");
        LocalTime requiredDueTime =
                Objects.requireNonNull(dueTime, "마감 시각은 필수입니다.");
        validateSchedule(
                requiredFrequency,
                weeklyDueDay,
                biweeklyDueDate
        );
        if (this.frequency == requiredFrequency
                && this.dueTime.equals(requiredDueTime)
                && Objects.equals(this.weeklyDueDay, weeklyDueDay)
                && Objects.equals(this.biweeklyDueDate, biweeklyDueDate)) {
            return false;
        }
        this.frequency = requiredFrequency;
        this.dueTime = requiredDueTime;
        this.weeklyDueDay = weeklyDueDay;
        this.biweeklyDueDate = biweeklyDueDate;
        this.scheduleRevision = Math.incrementExact(this.scheduleRevision);
        return true;
    }

    public void advancePlanningRevision() {
        this.scheduleRevision = Math.incrementExact(this.scheduleRevision);
    }

    public void recordEnrollmentChange() {
        this.eligibilityRevision = Math.incrementExact(this.eligibilityRevision);
    }

    private void validateSchedule() {
        validateSchedule(frequency, weeklyDueDay, biweeklyDueDate);
    }

    private static void validateSchedule(
            ChoreFrequency frequency,
            DayOfWeek weeklyDueDay,
            LocalDate biweeklyDueDate
    ) {
        switch (Objects.requireNonNull(frequency, "반복 주기는 필수입니다.")) {
            case DAILY -> {
                requireNull(weeklyDueDay, "일간 업무에는 주간 마감 요일을 설정할 수 없습니다.");
                requireNull(biweeklyDueDate, "일간 업무에는 격주 마감일을 설정할 수 없습니다.");
            }
            case WEEKLY -> {
                Objects.requireNonNull(weeklyDueDay, "주간 마감 요일은 필수입니다.");
                requireNull(biweeklyDueDate, "주간 업무에는 격주 마감일을 설정할 수 없습니다.");
            }
            case BIWEEKLY -> {
                requireNull(weeklyDueDay, "격주 업무에는 주간 마감 요일을 설정할 수 없습니다.");
                LocalDate requiredDueDate = Objects.requireNonNull(
                        biweeklyDueDate,
                        "격주 마감일은 필수입니다."
                );
                if (requiredDueDate.isAfter(MAX_BIWEEKLY_DUE_DATE)) {
                    throw new IllegalArgumentException("격주 마감일이 지원 날짜 범위를 벗어났습니다.");
                }
            }
        }
    }

    private static GroupMember requireMembershipOfGroup(GroupMember member, SharingGroup group) {
        GroupMember required = Objects.requireNonNull(member, "생성 멤버는 필수입니다.");
        if (required.getGroup() != group) {
            throw new IllegalArgumentException("생성 멤버는 업무와 같은 그룹에 속해야 합니다.");
        }
        if (!required.isActive()) {
            throw new IllegalArgumentException("탈퇴 멤버는 업무를 생성할 수 없습니다.");
        }
        return required;
    }

    private static String normalizeName(String name) {
        String normalized = Objects.requireNonNull(name, "업무명은 필수입니다.").trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("업무명은 비어 있을 수 없습니다.");
        }
        if (normalized.length() > 100) {
            throw new IllegalArgumentException("업무명은 100자 이하여야 합니다.");
        }
        return normalized;
    }

    private static void requireNull(Object value, String message) {
        if (value != null) {
            throw new IllegalArgumentException(message);
        }
    }
}
