package gdg.sharinglog.repository.rotation;

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

    Optional<ChoreOccurrence> findByChore_IdAndPeriodStart(Long choreId, LocalDate periodStart);

    Optional<ChoreOccurrence>
    findFirstByChore_IdAndPeriodStartBeforeOrderByPeriodStartDesc(
            Long choreId,
            LocalDate periodStart
    );

    Optional<ChoreOccurrence>
    findFirstByChore_IdOrderByPeriodEndExclusiveDescIdDesc(Long choreId);

    @EntityGraph(attributePaths = {"chore", "currentAssignment", "currentAssignment.assignee"})
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<ChoreOccurrence> findAllByCurrentAssignment_Assignee_IdAndStatusOrderByIdAsc(
            Long membershipId,
            OccurrenceStatus status
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
            order by occurrence.frequencySnapshot asc, occurrence.dueAt asc, occurrence.id asc
            """)
    List<ChoreOccurrence> findAllActiveOn(
            @Param("groupId") Long groupId,
            @Param("activeOn") LocalDate activeOn
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
    List<ChoreOccurrence>
    findAllByChore_Group_IdAndStatusAndCurrentAssignment_Assignee_IdOrderByDueAtAsc(
            Long groupId,
            OccurrenceStatus status,
            Long membershipId
    );
}
