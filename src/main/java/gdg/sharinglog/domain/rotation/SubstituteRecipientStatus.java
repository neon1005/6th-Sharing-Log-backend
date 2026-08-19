package gdg.sharinglog.domain.rotation;

public enum SubstituteRecipientStatus {
    PENDING,
    ACCEPTED,
    DECLINED,
    INELIGIBLE;

    public boolean isPending() {
        return this == PENDING;
    }
}
