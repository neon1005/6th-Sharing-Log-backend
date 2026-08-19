package gdg.sharinglog.rotation.engine;

import static gdg.sharinglog.rotation.engine.CandidateDecision.DECLINED_CURRENT_OCCURRENCE;
import static gdg.sharinglog.rotation.engine.CandidateDecision.HIGHER_ACTIVE_PERIOD_LOAD;
import static gdg.sharinglog.rotation.engine.CandidateDecision.HIGHER_EFFECTIVE_VALID_SAME_CHORE_ASSIGNMENT_COUNT;
import static gdg.sharinglog.rotation.engine.CandidateDecision.HIGHER_VALID_SAME_FREQUENCY_ASSIGNMENT_COUNT;
import static gdg.sharinglog.rotation.engine.CandidateDecision.INACTIVE;
import static gdg.sharinglog.rotation.engine.CandidateDecision.NOT_ELIGIBLE;
import static gdg.sharinglog.rotation.engine.CandidateDecision.PREVIOUS_ASSIGNEE_DEPRIORITIZED;
import static gdg.sharinglog.rotation.engine.CandidateDecision.RANDOM_TIE_NOT_SELECTED;
import static gdg.sharinglog.rotation.engine.CandidateDecision.SELECTED;
import static gdg.sharinglog.rotation.engine.SelectionReasonCode.MINIMUM_ACTIVE_PERIOD_LOAD;
import static gdg.sharinglog.rotation.engine.SelectionReasonCode.MINIMUM_EFFECTIVE_VALID_SAME_CHORE_ASSIGNMENT_COUNT;
import static gdg.sharinglog.rotation.engine.SelectionReasonCode.MINIMUM_VALID_SAME_FREQUENCY_ASSIGNMENT_COUNT;
import static gdg.sharinglog.rotation.engine.SelectionReasonCode.NO_ASSIGNABLE_CANDIDATE;
import static gdg.sharinglog.rotation.engine.SelectionReasonCode.PREVIOUS_ASSIGNEE_WAS_ONLY_REMAINING_OPTION;
import static gdg.sharinglog.rotation.engine.SelectionReasonCode.RANDOM_TIE_BREAK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

class RotationAssignmentEngineTest {

    @Test
    void availabilityFilterRunsBeforeEveryFairnessCriterion() {
        RotationAssignmentResult.Assigned result = assigned(
                deterministicEngine(),
                List.of(
                        candidate(1, false, true, false, 0, 0, 0, false),
                        candidate(2, true, false, false, 0, 0, 0, false),
                        candidate(3, true, true, true, 0, 0, 0, false),
                        candidate(4, true, true, false, 9, 9, 9, true)
                )
        );

        assertEquals(4L, result.selectedMembershipId());
        assertDecisions(result, Map.of(
                1L, INACTIVE,
                2L, NOT_ELIGIBLE,
                3L, DECLINED_CURRENT_OCCURRENCE,
                4L, SELECTED
        ));
    }

    @Test
    void minimumActivePeriodLoadHasPriorityOverCycleFrequencyAndPreviousAssignee() {
        RotationAssignmentResult.Assigned result = assigned(
                deterministicEngine(),
                List.of(
                        candidate(1, true, true, false, 1, 9, 9, true),
                        candidate(2, true, true, false, 2, 0, 0, false)
                )
        );

        assertEquals(2L, result.selectedMembershipId());
        assertDecisions(result, Map.of(
                1L, HIGHER_ACTIVE_PERIOD_LOAD,
                2L, SELECTED
        ));
        assertReason(result, MINIMUM_ACTIVE_PERIOD_LOAD);
    }

    @Test
    void minimumEffectiveValidSameChoreCountHasPriorityAfterPeriodLoadTies() {
        RotationAssignmentResult.Assigned result = assigned(
                deterministicEngine(),
                List.of(
                        candidate(1, true, true, false, 1, 9, 0, true),
                        candidate(2, true, true, false, 2, 0, 0, false)
                )
        );

        assertEquals(1L, result.selectedMembershipId());
        assertDecisions(result, Map.of(
                1L, SELECTED,
                2L, HIGHER_EFFECTIVE_VALID_SAME_CHORE_ASSIGNMENT_COUNT
        ));
        assertReason(result, MINIMUM_EFFECTIVE_VALID_SAME_CHORE_ASSIGNMENT_COUNT);
    }

    @Test
    void fairnessCreditContributesToEffectiveValidSameChoreCount() {
        RotationAssignmentResult.Assigned result = assigned(
                deterministicEngine(),
                List.of(
                        candidateWithCredit(1, 2, 0, 4, 0, false),
                        candidateWithCredit(2, 0, 3, 0, 0, false)
                )
        );

        assertEquals(1L, result.selectedMembershipId());
        assertDecisions(result, Map.of(
                1L, SELECTED,
                2L, HIGHER_EFFECTIVE_VALID_SAME_CHORE_ASSIGNMENT_COUNT
        ));

        CandidateSnapshot credited = snapshot(result, 2L);
        assertEquals(0L, credited.validSameChoreAssignmentCount());
        assertEquals(3L, credited.fairnessCredit());
        assertEquals(3L, credited.effectiveValidSameChoreAssignmentCount());
        assertEquals(0L, credited.validSameFrequencyAssignmentCount());
        assertEquals(0, credited.activePeriodLoad());
    }

    @Test
    void minimumValidSameFrequencyCountHasPriorityAfterEarlierCountsTie() {
        RotationAssignmentResult.Assigned result = assigned(
                deterministicEngine(),
                List.of(
                        candidate(1, true, true, false, 2, 0, 0, true),
                        candidate(2, true, true, false, 2, 1, 0, false)
                )
        );

        assertEquals(1L, result.selectedMembershipId());
        assertDecisions(result, Map.of(
                1L, SELECTED,
                2L, HIGHER_VALID_SAME_FREQUENCY_ASSIGNMENT_COUNT
        ));
        assertReason(result, MINIMUM_VALID_SAME_FREQUENCY_ASSIGNMENT_COUNT);
    }

    @Test
    void minimumActivePeriodLoadHasPriorityOverPreviousAssignee() {
        RotationAssignmentResult.Assigned result = assigned(
                deterministicEngine(),
                List.of(
                        candidate(1, true, true, false, 2, 4, 0, true),
                        candidate(2, true, true, false, 2, 4, 1, false)
                )
        );

        assertEquals(1L, result.selectedMembershipId());
        assertDecisions(result, Map.of(1L, SELECTED, 2L, HIGHER_ACTIVE_PERIOD_LOAD));
        assertReason(result, MINIMUM_ACTIVE_PERIOD_LOAD);
        assertReason(result, PREVIOUS_ASSIGNEE_WAS_ONLY_REMAINING_OPTION);
    }

    @Test
    void previousAssigneeIsDeprioritizedAfterEveryCountTies() {
        RotationAssignmentResult.Assigned result = assigned(
                deterministicEngine(),
                List.of(
                        candidate(1, true, true, false, 2, 4, 1, true),
                        candidate(2, true, true, false, 2, 4, 1, false)
                )
        );

        assertEquals(2L, result.selectedMembershipId());
        assertDecisions(result, Map.of(
                1L, PREVIOUS_ASSIGNEE_DEPRIORITIZED,
                2L, SELECTED
        ));
    }

    @Test
    void previousAssigneeCanWinWhenAnEarlierCriterionAlreadyRemovedAlternatives() {
        RotationAssignmentResult.Assigned result = assigned(
                deterministicEngine(),
                List.of(
                        candidate(1, true, true, false, 0, 9, 0, true),
                        candidate(2, true, true, false, 1, 0, 0, false)
                )
        );

        assertEquals(1L, result.selectedMembershipId());
        assertReason(result, PREVIOUS_ASSIGNEE_WAS_ONLY_REMAINING_OPTION);
    }

    @Test
    void finalTieUsesInjectedRandomOnlyAfterEveryDeterministicCriterionTies() {
        FixedIndexRandom random = new FixedIndexRandom(1);
        RotationAssignmentResult.Assigned result = assigned(
                new RotationAssignmentEngine(random),
                List.of(
                        candidate(3, true, true, false, 0, 0, 0, false),
                        candidate(1, true, true, false, 0, 0, 0, false),
                        candidate(2, true, true, false, 0, 0, 0, false)
                )
        );

        assertEquals(2L, result.selectedMembershipId());
        assertEquals(1, random.calls());
        assertEquals(3, random.lastBound());
        assertEquals(List.of(1L, 2L, 3L), result.candidateSnapshot().stream()
                .map(CandidateSnapshot::membershipId)
                .toList());
        assertDecisions(result, Map.of(
                1L, RANDOM_TIE_NOT_SELECTED,
                2L, SELECTED,
                3L, RANDOM_TIE_NOT_SELECTED
        ));
        assertReason(result, RANDOM_TIE_BREAK);
    }

    @Test
    void noAssignableCandidateIsAnExplicitAuditableResult() {
        RotationAssignmentResult result = new RotationAssignmentEngine(new Random(7)).assign(
                List.of(
                        candidate(1, false, true, false, 0, 0, 0, false),
                        candidate(2, true, false, false, 0, 0, 0, false),
                        candidate(3, true, true, true, 0, 0, 0, false)
                )
        );

        RotationAssignmentResult.NoCandidate noCandidate =
                assertInstanceOf(RotationAssignmentResult.NoCandidate.class, result);
        assertEquals(
                NoCandidateReason.NO_ACTIVE_ELIGIBLE_NON_DECLINED_CANDIDATE,
                noCandidate.reason()
        );
        assertDecisions(noCandidate, Map.of(
                1L, INACTIVE,
                2L, NOT_ELIGIBLE,
                3L, DECLINED_CURRENT_OCCURRENCE
        ));
        assertReason(noCandidate, NO_ASSIGNABLE_CANDIDATE);
    }

    @Test
    void duplicateMembershipIdsAreRejected() {
        RotationAssignmentEngine engine = new RotationAssignmentEngine(new Random(8));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> engine.assign(List.of(
                        candidate(1, true, true, false, 0, 0, 0, false),
                        candidate(1, true, true, false, 0, 0, 0, false)
                ))
        );

        assertEquals("Duplicate membershipId: 1", exception.getMessage());
    }

    private RotationCandidate candidate(
            long membershipId,
            boolean active,
            boolean eligible,
            boolean declined,
            long validSameChoreAssignmentCount,
            long validSameFrequencyAssignmentCount,
            int activePeriodLoad,
            boolean previousAssignee
    ) {
        return new RotationCandidate(
                membershipId,
                active,
                eligible,
                declined,
                validSameChoreAssignmentCount,
                0L,
                validSameFrequencyAssignmentCount,
                activePeriodLoad,
                previousAssignee
        );
    }

    private RotationCandidate candidateWithCredit(
            long membershipId,
            long validSameChoreAssignmentCount,
            long fairnessCredit,
            long validSameFrequencyAssignmentCount,
            int activePeriodLoad,
            boolean previousAssignee
    ) {
        return new RotationCandidate(
                membershipId,
                true,
                true,
                false,
                validSameChoreAssignmentCount,
                fairnessCredit,
                validSameFrequencyAssignmentCount,
                activePeriodLoad,
                previousAssignee
        );
    }

    private RotationAssignmentEngine deterministicEngine() {
        return new RotationAssignmentEngine(new FailOnUseRandom());
    }

    private RotationAssignmentResult.Assigned assigned(
            RotationAssignmentEngine engine,
            List<RotationCandidate> candidates
    ) {
        return assertInstanceOf(
                RotationAssignmentResult.Assigned.class,
                engine.assign(candidates)
        );
    }

    private CandidateSnapshot snapshot(RotationAssignmentResult result, long membershipId) {
        return result.candidateSnapshot().stream()
                .filter(candidate -> candidate.membershipId() == membershipId)
                .findFirst()
                .orElseThrow();
    }

    private void assertDecisions(
            RotationAssignmentResult result,
            Map<Long, CandidateDecision> expected
    ) {
        Map<Long, CandidateDecision> actual = result.candidateSnapshot().stream()
                .collect(Collectors.toMap(
                        CandidateSnapshot::membershipId,
                        CandidateSnapshot::decision
                ));
        assertEquals(expected, actual);
    }

    private void assertReason(
            RotationAssignmentResult result,
            SelectionReasonCode expected
    ) {
        assertTrue(result.selectionReasons().stream()
                .anyMatch(reason -> reason.code() == expected));
    }

    private static final class FailOnUseRandom extends Random {

        private static final long serialVersionUID = 1L;

        @Override
        public int nextInt(int bound) {
            throw new AssertionError("Random must not be used after a deterministic decision");
        }
    }

    private static final class FixedIndexRandom extends Random {

        private static final long serialVersionUID = 1L;

        private final int selectedIndex;
        private int calls;
        private int lastBound;

        private FixedIndexRandom(int selectedIndex) {
            this.selectedIndex = selectedIndex;
        }

        @Override
        public int nextInt(int bound) {
            if (selectedIndex >= bound) {
                throw new AssertionError("Fixed index must be less than the finalist count");
            }
            calls++;
            lastBound = bound;
            return selectedIndex;
        }

        private int calls() {
            return calls;
        }

        private int lastBound() {
            return lastBound;
        }
    }
}
