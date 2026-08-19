package gdg.sharinglog.rotation.engine;

/**
 * The first decisive reason a candidate was selected or removed.
 */
public enum CandidateDecision {
    INACTIVE,
    NOT_ELIGIBLE,
    DECLINED_CURRENT_OCCURRENCE,
    HIGHER_EFFECTIVE_VALID_SAME_CHORE_ASSIGNMENT_COUNT,
    HIGHER_VALID_SAME_FREQUENCY_ASSIGNMENT_COUNT,
    HIGHER_ACTIVE_PERIOD_LOAD,
    PREVIOUS_ASSIGNEE_DEPRIORITIZED,
    RANDOM_TIE_NOT_SELECTED,
    SELECTED
}
