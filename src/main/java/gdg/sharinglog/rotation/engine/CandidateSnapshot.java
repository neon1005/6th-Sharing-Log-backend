package gdg.sharinglog.rotation.engine;

import java.util.Objects;

/**
 * An immutable audit snapshot of a candidate and the assignment decision.
 */
public record CandidateSnapshot(
        long membershipId,
        boolean active,
        boolean eligible,
        boolean declinedCurrentOccurrence,
        long validSameChoreAssignmentCount,
        long fairnessCredit,
        long effectiveValidSameChoreAssignmentCount,
        long validSameFrequencyAssignmentCount,
        int activePeriodLoad,
        boolean previousAssignee,
        CandidateDecision decision
) {

    public CandidateSnapshot {
        Objects.requireNonNull(decision, "decision must not be null");
    }

    static CandidateSnapshot from(RotationCandidate candidate, CandidateDecision decision) {
        return new CandidateSnapshot(
                candidate.membershipId(),
                candidate.active(),
                candidate.eligible(),
                candidate.declinedCurrentOccurrence(),
                candidate.validSameChoreAssignmentCount(),
                candidate.fairnessCredit(),
                candidate.effectiveValidSameChoreAssignmentCount(),
                candidate.validSameFrequencyAssignmentCount(),
                candidate.activePeriodLoad(),
                candidate.previousAssignee(),
                decision
        );
    }
}
