package gdg.sharinglog.repository.rotation;

import java.util.List;
import java.util.Optional;

import gdg.sharinglog.domain.rotation.SubstituteRequest;
import gdg.sharinglog.domain.rotation.SubstituteRequestStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubstituteRequestRepository extends JpaRepository<SubstituteRequest, Long> {

    @EntityGraph(attributePaths = {
            "requesterAssignment",
            "requesterAssignment.assignee"
    })
    Optional<SubstituteRequest> findByOccurrence_IdAndActiveMarker(
            Long occurrenceId,
            Integer activeMarker
    );

    @EntityGraph(attributePaths = {
            "occurrence",
            "occurrence.chore",
            "requesterAssignment",
            "requesterAssignment.assignee",
            "requesterAssignment.assignee.user",
            "acceptedAssignment",
            "acceptedAssignment.assignee",
            "acceptedAssignment.assignee.user"
    })
    Optional<SubstituteRequest>
    findByPublicIdAndOccurrence_Chore_Group_PublicId(
            String publicId,
            String groupPublicId
    );

    @EntityGraph(attributePaths = {
            "occurrence",
            "occurrence.chore",
            "requesterAssignment",
            "requesterAssignment.assignee",
            "requesterAssignment.assignee.user",
            "acceptedAssignment",
            "acceptedAssignment.assignee",
            "acceptedAssignment.assignee.user"
    })
    List<SubstituteRequest>
    findAllByOccurrence_Chore_Group_IdOrderByCreatedAtDescIdDesc(Long groupId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {
            "occurrence",
            "occurrence.chore",
            "requesterAssignment",
            "requesterAssignment.assignee",
            "acceptedAssignment",
            "acceptedAssignment.assignee"
    })
    @Query("""
            select request
            from SubstituteRequest request
            where request.publicId = :publicId
              and request.occurrence.chore.group.publicId = :groupPublicId
            """)
    Optional<SubstituteRequest> findByPublicIdAndGroupPublicIdForUpdate(
            @Param("publicId") String publicId,
            @Param("groupPublicId") String groupPublicId
    );

    @Query("""
            select request.occurrence.publicId
            from SubstituteRequest request
            where request.publicId = :publicId
              and request.occurrence.chore.group.publicId = :groupPublicId
            """)
    Optional<String> findOccurrencePublicId(
            @Param("publicId") String publicId,
            @Param("groupPublicId") String groupPublicId
    );

    List<SubstituteRequest> findAllByStatus(SubstituteRequestStatus status);
}
