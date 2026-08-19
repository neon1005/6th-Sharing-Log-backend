package gdg.sharinglog.repository.rotation;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import gdg.sharinglog.domain.rotation.ChoreOccurrence;
import gdg.sharinglog.domain.rotation.OccurrenceStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;

public interface ChoreOccurrenceRepository extends JpaRepository<ChoreOccurrence, Long> {

    @EntityGraph(attributePaths = {"chore", "currentAssignment", "currentAssignment.assignee"})
    Optional<ChoreOccurrence> findByPublicId(String publicId);

    @EntityGraph(attributePaths = {
            "chore",
            "currentAssignment",
            "currentAssignment.assignee",
            "currentAssignment.assignee.user"
    })
    Optional<ChoreOccurrence> findByPublicIdAndChore_Group_PublicId(
            String publicId,
            String groupPublicId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {
            "chore",
            "currentAssignment",
            "currentAssignment.assignee",
            "currentAssignment.assignee.user"
    })
    @Query("""
            select occurrence
            from ChoreOccurrence occurrence
            where occurrence.publicId = :publicId
              and occurrence.chore.group.publicId = :groupPublicId
            """)
    Optional<ChoreOccurrence> findByPublicIdAndGroupPublicIdForUpdate(
            @Param("publicId") String publicId,
            @Param("groupPublicId") String groupPublicId
    );

    @Query("""
            select occurrence.chore.group.id
            from ChoreOccurrence occurrence
            where occurrence.publicId = :publicId
            """)
    Optional<Long> findGroupIdByPublicId(@Param("publicId") String publicId);

    @Query("""
            select occurrence.chore.group.id
            from ChoreOccurrence occurrence
            where occurrence.id = :occurrenceId
            """)
    Optional<Long> findGroupIdById(@Param("occurrenceId") Long occurrenceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"chore", "currentAssignment", "currentAssignment.assignee"})
    @Query("select occurrence from ChoreOccurrence occurrence where occurrence.publicId = :publicId")
    Optional<ChoreOccurrence> findByPublicIdForUpdate(@Param("publicId") String publicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"chore", "currentAssignment", "currentAssignment.assignee"})
    @Query("select occurrence from ChoreOccurrence occurrence where occurrence.id = :occurrenceId")
    Optional<ChoreOccurrence> findByIdForUpdate(@Param("occurrenceId") Long occurrenceId);

    Optional<ChoreOccurrence> findByChore_IdAndScheduleRevisionSnapshotAndPeriodStartAndStatusNot(
            Long choreId,
            long scheduleRevisionSnapshot,
            LocalDate periodStart,
            OccurrenceStatus excludedStatus
    );

    @Query("""
            select occurrence
            from ChoreOccurrence occurrence
            where occurrence.chore.id = :choreId
              and occurrence.periodStart < :periodStart
              and occurrence.status <> gdg.sharinglog.domain.rotation.OccurrenceStatus.CANCELLED
            order by occurrence.periodStart desc,
                     occurrence.scheduleRevisionSnapshot desc,
                     occurrence.id desc
            """)
    List<ChoreOccurrence> findAllNonCancelledBefore(
            @Param("choreId") Long choreId,
            @Param("periodStart") LocalDate periodStart,
            Pageable pageable
    );

    @Query("""
            select occurrence
            from ChoreOccurrence occurrence
            where occurrence.chore.id = :choreId
              and occurrence.periodStart < :toExclusive
              and occurrence.periodEndExclusive > :fromInclusive
              and occurrence.status <> gdg.sharinglog.domain.rotation.OccurrenceStatus.CANCELLED
            order by occurrence.scheduleRevisionSnapshot desc, occurrence.id desc
            """)
    List<ChoreOccurrence> findAllNonCancelledOverlapping(
            @Param("choreId") Long choreId,
            @Param("fromInclusive") LocalDate fromInclusive,
            @Param("toExclusive") LocalDate toExclusive,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"chore", "currentAssignment", "currentAssignment.assignee"})
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select occurrence
            from ChoreOccurrence occurrence
            where occurrence.chore.id = :choreId
              and occurrence.periodStart <= :activeOn
              and occurrence.periodEndExclusive > :activeOn
              and occurrence.status in (
                  gdg.sharinglog.domain.rotation.OccurrenceStatus.ASSIGNED,
                  gdg.sharinglog.domain.rotation.OccurrenceStatus.NEEDS_ATTENTION
              )
            order by occurrence.id asc
            """)
    List<ChoreOccurrence> findAllOpenActiveOnByChoreIdForUpdate(
            @Param("choreId") Long choreId,
            @Param("activeOn") LocalDate activeOn
    );

    @EntityGraph(attributePaths = {"chore", "currentAssignment", "currentAssignment.assignee"})
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select occurrence
            from ChoreOccurrence occurrence
            where occurrence.currentAssignment.assignee.id = :membershipId
              and occurrence.status = gdg.sharinglog.domain.rotation.OccurrenceStatus.ASSIGNED
              and occurrence.periodStart <= :activeOn
            order by occurrence.id asc
            """)
    List<ChoreOccurrence> findAllCurrentOrPastAssignedToMemberForUpdate(
            @Param("membershipId") Long membershipId,
            @Param("activeOn") LocalDate activeOn
    );

    @EntityGraph(attributePaths = {
            "chore",
            "currentAssignment",
            "currentAssignment.assignee"
    })
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select occurrence
            from ChoreOccurrence occurrence
            where occurrence.chore.id = :choreId
              and occurrence.status in (
                  gdg.sharinglog.domain.rotation.OccurrenceStatus.ASSIGNED,
                  gdg.sharinglog.domain.rotation.OccurrenceStatus.NEEDS_ATTENTION
              )
            order by occurrence.id asc
            """)
    List<ChoreOccurrence> findAllOpenByChoreIdForUpdate(@Param("choreId") Long choreId);

    @EntityGraph(attributePaths = {"chore", "currentAssignment", "currentAssignment.assignee"})
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select occurrence
            from ChoreOccurrence occurrence
            where occurrence.chore.id = :choreId
              and occurrence.periodStart > :activeOn
              and occurrence.status in (
                  gdg.sharinglog.domain.rotation.OccurrenceStatus.ASSIGNED,
                  gdg.sharinglog.domain.rotation.OccurrenceStatus.NEEDS_ATTENTION
              )
            order by occurrence.periodStart asc, occurrence.id asc
            """)
    List<ChoreOccurrence> findAllFutureOpenByChoreIdForUpdate(
            @Param("choreId") Long choreId,
            @Param("activeOn") LocalDate activeOn
    );

    @EntityGraph(attributePaths = {"chore", "currentAssignment", "currentAssignment.assignee"})
    List<ChoreOccurrence> findAllByChore_Group_IdAndPeriodStartBetweenOrderByPeriodStartAscIdAsc(
            Long groupId,
            LocalDate fromInclusive,
            LocalDate toInclusive
    );

    @EntityGraph(attributePaths = {
            "chore",
            "currentAssignment",
            "currentAssignment.assignee",
            "currentAssignment.assignee.user"
    })
    @Query("""
            select occurrence
            from ChoreOccurrence occurrence
            where occurrence.chore.group.id = :groupId
              and occurrence.periodStart <= :activeOn
              and occurrence.periodEndExclusive > :activeOn
              and occurrence.status <> gdg.sharinglog.domain.rotation.OccurrenceStatus.CANCELLED
            order by occurrence.frequencySnapshot asc, occurrence.dueAt asc, occurrence.id asc
            """)
    List<ChoreOccurrence> findAllActiveOn(
            @Param("groupId") Long groupId,
            @Param("activeOn") LocalDate activeOn
    );

    @EntityGraph(attributePaths = {
            "chore",
            "currentAssignment",
            "currentAssignment.assignee",
            "currentAssignment.assignee.user"
    })
    @Query("""
            select occurrence
            from ChoreOccurrence occurrence
            where occurrence.chore.group.id = :groupId
              and occurrence.chore.active = true
              and occurrence.status <> gdg.sharinglog.domain.rotation.OccurrenceStatus.CANCELLED
              and occurrence.periodStart < :toExclusive
              and occurrence.periodEndExclusive > :fromInclusive
            order by occurrence.periodStart asc, occurrence.dueAt asc, occurrence.id asc
            """)
    List<ChoreOccurrence> findAllPlannedOverlapping(
            @Param("groupId") Long groupId,
            @Param("fromInclusive") LocalDate fromInclusive,
            @Param("toExclusive") LocalDate toExclusive
    );

    @EntityGraph(attributePaths = {"chore"})
    List<ChoreOccurrence>
    findAllByChore_Group_IdAndStatusOrderByClosedAtDescIdDesc(
            Long groupId,
            OccurrenceStatus status
    );

    @EntityGraph(attributePaths = {
            "chore",
            "currentAssignment",
            "currentAssignment.assignee",
            "currentAssignment.assignee.user"
    })
    @Query("""
            select occurrence
            from ChoreOccurrence occurrence
            where occurrence.chore.group.id = :groupId
              and occurrence.chore.active = true
              and occurrence.status = gdg.sharinglog.domain.rotation.OccurrenceStatus.ASSIGNED
              and occurrence.currentAssignment.assignee.id = :membershipId
              and occurrence.dueAt >= :fromInclusive
              and occurrence.dueAt <= :toInclusive
            order by occurrence.dueAt asc, occurrence.id asc
            """)
    List<ChoreOccurrence> findAllAssignedToMemberDueBetween(
            @Param("groupId") Long groupId,
            @Param("membershipId") Long membershipId,
            @Param("fromInclusive") Instant fromInclusive,
            @Param("toInclusive") Instant toInclusive
    );

    @EntityGraph(attributePaths = {
            "chore",
            "currentAssignment",
            "currentAssignment.assignee",
            "currentAssignment.assignee.user"
    })
    @Query("""
            select occurrence
            from ChoreOccurrence occurrence
            where occurrence.status = gdg.sharinglog.domain.rotation.OccurrenceStatus.ASSIGNED
              and occurrence.dueSoon24hNotifiedAt is null
              and occurrence.dueAt > :now
              and occurrence.dueAt <= :threshold
            """)
    List<ChoreOccurrence> findAllNeedingDueSoon24hNotification(
            @Param("now") Instant now,
            @Param("threshold") Instant threshold
    );

    @EntityGraph(attributePaths = {
            "chore",
            "currentAssignment",
            "currentAssignment.assignee",
            "currentAssignment.assignee.user"
    })
    @Query("""
            select occurrence
            from ChoreOccurrence occurrence
            where occurrence.status = gdg.sharinglog.domain.rotation.OccurrenceStatus.ASSIGNED
              and occurrence.dueSoon3hNotifiedAt is null
              and occurrence.dueAt > :now
              and occurrence.dueAt <= :threshold
            """)
    List<ChoreOccurrence> findAllNeedingDueSoon3hNotification(
            @Param("now") Instant now,
            @Param("threshold") Instant threshold
    );
}
