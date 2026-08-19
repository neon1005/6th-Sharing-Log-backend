package gdg.sharinglog.repository.rotation;

import java.util.List;

import gdg.sharinglog.domain.rotation.RotationDecisionLog;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RotationDecisionLogRepository extends JpaRepository<RotationDecisionLog, Long> {

    long countByOccurrence_Id(Long occurrenceId);

    @EntityGraph(attributePaths = "selectedMember")
    List<RotationDecisionLog> findAllByOccurrence_IdOrderByDecisionSequence(Long occurrenceId);
}
