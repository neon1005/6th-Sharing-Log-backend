package gdg.sharinglog.domain.rotation;

public enum OccurrenceStatus {
    ASSIGNED,
    COMPLETED,
    SKIPPED,
    CANCELLED,
    NEEDS_ATTENTION;

    public boolean isTerminal() {
        return this == COMPLETED || this == SKIPPED || this == CANCELLED;
    }
}
