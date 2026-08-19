package gdg.sharinglog.rotation.engine;

import java.util.List;
import java.util.Objects;

/**
 * The assignment outcome. A missing candidate is represented as data rather than
 * by an exception so callers can persist or surface the unassigned state.
 */
public sealed interface RotationAssignmentResult
        permits RotationAssignmentResult.Assigned, RotationAssignmentResult.NoCandidate {

    List<CandidateSnapshot> candidateSnapshot();

    List<SelectionReason> selectionReasons();

    record Assigned(
            long selectedMembershipId,
            List<CandidateSnapshot> candidateSnapshot,
            List<SelectionReason> selectionReasons
    ) implements RotationAssignmentResult {

        public Assigned {
            if (selectedMembershipId <= 0) {
                throw new IllegalArgumentException("selectedMembershipId must be positive");
            }
            candidateSnapshot = List.copyOf(
                    Objects.requireNonNull(candidateSnapshot, "candidateSnapshot must not be null")
            );
            selectionReasons = List.copyOf(
                    Objects.requireNonNull(selectionReasons, "selectionReasons must not be null")
            );
        }
    }

    record NoCandidate(
            NoCandidateReason reason,
            List<CandidateSnapshot> candidateSnapshot,
            List<SelectionReason> selectionReasons
    ) implements RotationAssignmentResult {

        public NoCandidate {
            Objects.requireNonNull(reason, "reason must not be null");
            candidateSnapshot = List.copyOf(
                    Objects.requireNonNull(candidateSnapshot, "candidateSnapshot must not be null")
            );
            selectionReasons = List.copyOf(
                    Objects.requireNonNull(selectionReasons, "selectionReasons must not be null")
            );
        }
    }
}
