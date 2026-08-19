package gdg.sharinglog.rotation.engine;

/**
 * A point-in-time input used to choose an assignee for one occurrence.
 */
public record RotationCandidate(
        long membershipId,
        boolean active,
        boolean eligible,
        boolean declinedCurrentOccurrence,
        long validSameChoreAssignmentCount,
        long fairnessCredit,
        long validSameFrequencyAssignmentCount,
        int activePeriodLoad,
        boolean previousAssignee
) {

    public RotationCandidate {
        if (membershipId <= 0) {
            throw new IllegalArgumentException("membershipId must be positive");
        }
        if (validSameChoreAssignmentCount < 0) {
            throw new IllegalArgumentException(
                    "validSameChoreAssignmentCount must not be negative"
            );
        }
        if (fairnessCredit < 0) {
            throw new IllegalArgumentException("fairnessCredit must not be negative");
        }
        if (validSameFrequencyAssignmentCount < 0) {
            throw new IllegalArgumentException(
                    "validSameFrequencyAssignmentCount must not be negative"
            );
        }
        if (activePeriodLoad < 0) {
            throw new IllegalArgumentException("activePeriodLoad must not be negative");
        }
    }

    public long effectiveValidSameChoreAssignmentCount() {
        return Math.addExact(validSameChoreAssignmentCount, fairnessCredit);
    }
}
