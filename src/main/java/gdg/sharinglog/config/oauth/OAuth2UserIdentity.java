package gdg.sharinglog.config.oauth;

import gdg.sharinglog.domain.OAuthProvider;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.util.StringUtils;

public record OAuth2UserIdentity(OAuthProvider provider, String providerUserId) {

    public static OAuth2UserIdentity from(String registrationId, OAuth2User oAuth2User) {
        OAuthProvider provider = OAuthProvider.fromRegistrationId(registrationId);
        Object value = oAuth2User.getAttribute(provider.getUserIdAttribute());

        if (value == null || !StringUtils.hasText(value.toString())) {
            throw new IllegalArgumentException(
                    "OAuth 사용자 정보에 " + provider.getUserIdAttribute() + " 값이 없습니다."
            );
        }

        return new OAuth2UserIdentity(provider, value.toString().trim());
    }
}
