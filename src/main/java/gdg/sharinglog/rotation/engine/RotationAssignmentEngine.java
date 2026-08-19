package gdg.sharinglog.rotation.engine;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.random.RandomGenerator;

/**
 * Selects one assignee using fairness criteria in a strict order.
 *
 * <p>The random generator is consulted only after every deterministic criterion
 * has been applied and more than one finalist remains.</p>
 */
public final class RotationAssignmentEngine {

    private static final Comparator<RotationCandidate> BY_MEMBERSHIP_ID =
            Comparator.comparingLong(RotationCandidate::membershipId);

    private final RandomGenerator randomGenerator;

    public RotationAssignmentEngine(RandomGenerator randomGenerator) {
        this.randomGenerator = Objects.requireNonNull(randomGenerator, "randomGenerator must not be null");
    }

    public RotationAssignmentResult assign(Collection<RotationCandidate> candidates) {
        List<RotationCandidate> orderedCandidates = validateAndOrder(candidates);
        Map<Long, CandidateDecision> decisions = new HashMap<>();
        List<SelectionReason> reasons = new ArrayList<>();
        List<RotationCandidate> finalists = new ArrayList<>();

        for (RotationCandidate candidate : orderedCandidates) {
            CandidateDecision filterDecision = filterDecision(candidate);
            if (filterDecision == null) {
                finalists.add(candidate);
            } else {
                decisions.put(candidate.membershipId(), filterDecision);
            }
        }

        reasons.add(reason(
                SelectionReasonCode.ACTIVE_ELIGIBLE_NOT_DECLINED_FILTER,
                finalists.size() + " of " + orderedCandidates.size()
                        + " candidates remained after the availability filter"
        ));

        if (finalists.isEmpty()) {
            reasons.add(reason(
                    SelectionReasonCode.NO_ASSIGNABLE_CANDIDATE,
                    "No active, eligible candidate remained who had not declined this occurrence"
            ));
            return new RotationAssignmentResult.NoCandidate(
                    NoCandidateReason.NO_ACTIVE_ELIGIBLE_NON_DECLINED_CANDIDATE,
                    snapshots(orderedCandidates, decisions),
                    reasons
            );
        }

        int minimumActivePeriodLoad = finalists.stream()
                .mapToInt(RotationCandidate::activePeriodLoad)
                .min()
                .orElseThrow();
        eliminate(
                finalists,
                decisions,
                candidate -> candidate.activePeriodLoad() > minimumActivePeriodLoad,
                CandidateDecision.HIGHER_ACTIVE_PERIOD_LOAD
        );
        reasons.add(reason(
                SelectionReasonCode.MINIMUM_ACTIVE_PERIOD_LOAD,
                "Kept candidates with the minimum active-period load of "
                        + minimumActivePeriodLoad
        ));

        long minimumEffectiveValidSameChoreAssignmentCount = finalists.stream()
                .mapToLong(RotationCandidate::effectiveValidSameChoreAssignmentCount)
                .min()
                .orElseThrow();
        eliminate(
                finalists,
                decisions,
                candidate ->
                        candidate.effectiveValidSameChoreAssignmentCount()
                                > minimumEffectiveValidSameChoreAssignmentCount,
                CandidateDecision.HIGHER_EFFECTIVE_VALID_SAME_CHORE_ASSIGNMENT_COUNT
        );
        reasons.add(reason(
                SelectionReasonCode.MINIMUM_EFFECTIVE_VALID_SAME_CHORE_ASSIGNMENT_COUNT,
                "Kept candidates with the minimum effective valid same-chore assignment "
                        + "count of " + minimumEffectiveValidSameChoreAssignmentCount
        ));

        long minimumValidSameFrequencyAssignmentCount = finalists.stream()
                .mapToLong(RotationCandidate::validSameFrequencyAssignmentCount)
                .min()
                .orElseThrow();
        eliminate(
                finalists,
                decisions,
                candidate -> candidate.validSameFrequencyAssignmentCount()
                        > minimumValidSameFrequencyAssignmentCount,
                CandidateDecision.HIGHER_VALID_SAME_FREQUENCY_ASSIGNMENT_COUNT
        );
        reasons.add(reason(
                SelectionReasonCode.MINIMUM_VALID_SAME_FREQUENCY_ASSIGNMENT_COUNT,
                "Kept candidates with the minimum valid same-frequency assignment count of "
                        + minimumValidSameFrequencyAssignmentCount
        ));

        boolean hasPreviousAssignee = finalists.stream().anyMatch(RotationCandidate::previousAssignee);
        boolean hasAlternative = finalists.stream().anyMatch(candidate -> !candidate.previousAssignee());
        if (hasPreviousAssignee && hasAlternative) {
            eliminate(
                    finalists,
                    decisions,
                    RotationCandidate::previousAssignee,
                    CandidateDecision.PREVIOUS_ASSIGNEE_DEPRIORITIZED
            );
            reasons.add(reason(
                    SelectionReasonCode.PREVIOUS_ASSIGNEE_DEPRIORITIZED,
                    "Removed the previous assignee because an equally fair alternative remained"
            ));
        } else if (hasPreviousAssignee) {
            reasons.add(reason(
                    SelectionReasonCode.PREVIOUS_ASSIGNEE_WAS_ONLY_REMAINING_OPTION,
                    "Kept the previous assignee because no equally fair alternative remained"
            ));
        }

        RotationCandidate selected;
        if (finalists.size() == 1) {
            selected = finalists.getFirst();
            reasons.add(reason(
                    SelectionReasonCode.SOLE_FINALIST,
                    "Selected the only candidate remaining after deterministic criteria"
            ));
        } else {
            int selectedIndex = randomGenerator.nextInt(finalists.size());
            selected = finalists.get(selectedIndex);
            reasons.add(reason(
                    SelectionReasonCode.RANDOM_TIE_BREAK,
                    "Selected 1 of " + finalists.size()
                            + " equally ranked finalists using the injected random generator"
            ));
            for (RotationCandidate finalist : finalists) {
                if (finalist.membershipId() != selected.membershipId()) {
                    decisions.put(finalist.membershipId(), CandidateDecision.RANDOM_TIE_NOT_SELECTED);
                }
            }
        }

        decisions.put(selected.membershipId(), CandidateDecision.SELECTED);
        return new RotationAssignmentResult.Assigned(
                selected.membershipId(),
                snapshots(orderedCandidates, decisions),
                reasons
        );
    }

    private List<RotationCandidate> validateAndOrder(Collection<RotationCandidate> candidates) {
        Objects.requireNonNull(candidates, "candidates must not be null");

        List<RotationCandidate> ordered = new ArrayList<>(candidates.size());
        Set<Long> membershipIds = new HashSet<>();
        for (RotationCandidate candidate : candidates) {
            Objects.requireNonNull(candidate, "candidates must not contain null");
            if (!membershipIds.add(candidate.membershipId())) {
                throw new IllegalArgumentException(
                        "Duplicate membershipId: " + candidate.membershipId()
                );
            }
            ordered.add(candidate);
        }
        ordered.sort(BY_MEMBERSHIP_ID);
        return ordered;
    }

    private CandidateDecision filterDecision(RotationCandidate candidate) {
        if (!candidate.active()) {
            return CandidateDecision.INACTIVE;
        }
        if (!candidate.eligible()) {
            return CandidateDecision.NOT_ELIGIBLE;
        }
        if (candidate.declinedCurrentOccurrence()) {
            return CandidateDecision.DECLINED_CURRENT_OCCURRENCE;
        }
        return null;
    }

    private void eliminate(
            List<RotationCandidate> finalists,
            Map<Long, CandidateDecision> decisions,
            java.util.function.Predicate<RotationCandidate> shouldEliminate,
            CandidateDecision decision
    ) {
        finalists.removeIf(candidate -> {
            if (!shouldEliminate.test(candidate)) {
                return false;
            }
            decisions.put(candidate.membershipId(), decision);
            return true;
        });
    }

    private List<CandidateSnapshot> snapshots(
            List<RotationCandidate> orderedCandidates,
            Map<Long, CandidateDecision> decisions
    ) {
        return orderedCandidates.stream()
                .map(candidate -> CandidateSnapshot.from(
                        candidate,
                        Objects.requireNonNull(
                                decisions.get(candidate.membershipId()),
                                "Every candidate must have a final decision"
                        )
                ))
                .toList();
    }

    private SelectionReason reason(SelectionReasonCode code, String detail) {
        return new SelectionReason(code, detail);
    }
}
