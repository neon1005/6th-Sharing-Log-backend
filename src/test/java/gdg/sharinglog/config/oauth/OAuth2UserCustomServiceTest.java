package gdg.sharinglog.config.oauth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

class OAuth2UserCustomServiceTest {

    @Test
    void normalizeNaverUserFlattensResponseAttributes() {
        Map<String, Object> profile = Map.of(
                "id", "naver-user-id",
                "name", "네이버 사용자",
                "email", "naver@example.com",
                "profile_image", "https://example.com/profile.png"
        );
        OAuth2User rawUser = new DefaultOAuth2User(
                Set.of(new SimpleGrantedAuthority("ROLE_USER")),
                Map.of("resultcode", "00", "message", "success", "response", profile),
                "response"
        );

        OAuth2User normalized = OAuth2UserCustomService.normalizeNaverUser(rawUser);

        assertEquals("naver-user-id", normalized.getName());
        assertEquals("네이버 사용자", normalized.getAttribute("name"));
        assertEquals("naver@example.com", normalized.getAttribute("email"));
        assertEquals("https://example.com/profile.png", normalized.getAttribute("profile_image"));
    }

    @Test
    void normalizeNaverUserRejectsMissingResponse() {
        OAuth2User rawUser = new DefaultOAuth2User(
                Set.of(new SimpleGrantedAuthority("ROLE_USER")),
                Map.of("id", "unexpected-shape"),
                "id"
        );

        assertThrows(OAuth2AuthenticationException.class,
                () -> OAuth2UserCustomService.normalizeNaverUser(rawUser));
    }
}
