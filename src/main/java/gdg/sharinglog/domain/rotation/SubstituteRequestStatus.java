package gdg.sharinglog.domain.rotation;

public enum SubstituteRequestStatus {
    PENDING,
    ACCEPTED,
    EXHAUSTED,
    CANCELLED;

    public boolean isPending() {
        return this == PENDING;
    }
}
