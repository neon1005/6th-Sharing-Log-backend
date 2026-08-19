package gdg.sharinglog.domain.rotation;

import java.util.List;

public enum AssignmentEndReason {
    COMPLETED,
    SKIPPED_ALREADY_DONE,
    DECLINED_BY_ASSIGNEE,
    ASSIGNEE_LEFT_GROUP,
    PARTICIPATION_REMOVED,
    SUBSTITUTE_ACCEPTED,
    PLAN_REGENERATED;

    public static final List<AssignmentEndReason> SAME_OCCURRENCE_EXCLUSIONS =
            List.of(DECLINED_BY_ASSIGNEE, SUBSTITUTE_ACCEPTED);

    public static final List<AssignmentEndReason> SUBSTITUTE_REQUEST_RECIPIENT_EXCLUSIONS =
            List.of(DECLINED_BY_ASSIGNEE);

    public boolean requiresReassignment() {
        return this == DECLINED_BY_ASSIGNEE
                || this == ASSIGNEE_LEFT_GROUP
                || this == PARTICIPATION_REMOVED
                || this == SUBSTITUTE_ACCEPTED;
    }
}
