package gdg.sharinglog.repository.rotation;

import java.util.List;

import gdg.sharinglog.domain.rotation.OccurrenceEligibleMember;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OccurrenceEligibleMemberRepository
        extends JpaRepository<OccurrenceEligibleMember, Long> {

    @EntityGraph(attributePaths = "member")
    List<OccurrenceEligibleMember>
    findAllByOccurrence_IdAndSnapshotVersionOrderById(
            Long occurrenceId,
            int snapshotVersion
    );

    long countByOccurrence_IdAndSnapshotVersion(Long occurrenceId, int snapshotVersion);
}
