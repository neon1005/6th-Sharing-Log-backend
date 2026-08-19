package gdg.sharinglog.domain;

import java.util.Locale;

public enum OAuthProvider {
    GOOGLE("sub"),
    NAVER("id");

    private final String userIdAttribute;

    OAuthProvider(String userIdAttribute) {
        this.userIdAttribute = userIdAttribute;
    }

    public String getUserIdAttribute() {
        return userIdAttribute;
    }

    public static OAuthProvider fromRegistrationId(String registrationId) {
        if (registrationId == null || registrationId.isBlank()) {
            throw new IllegalArgumentException("OAuth 제공자 ID가 없습니다.");
        }

        try {
            return valueOf(registrationId.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("지원하지 않는 OAuth 제공자입니다: " + registrationId, exception);
        }
    }
}
