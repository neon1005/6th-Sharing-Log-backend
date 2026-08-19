package gdg.sharinglog.config.oauth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;
import java.util.Set;

import gdg.sharinglog.domain.OAuthProvider;
import gdg.sharinglog.domain.User;
import gdg.sharinglog.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class OAuth2UserPersistenceServiceTest {

    @Autowired
    OAuth2UserPersistenceService persistenceService;

    @Autowired
    UserRepository userRepository;

    @Test
    void savesGoogleUserUsingProviderAndSubject() {
        OAuth2User googleUser = oauth2User(
                Map.of("sub", "google-user-id", "email", "Google@Example.com"),
                "sub"
        );

        User savedUser = persistenceService.saveOrUpdate("google", googleUser);

        assertEquals(OAuthProvider.GOOGLE, savedUser.getProvider());
        assertEquals("google-user-id", savedUser.getProviderUserId());
        assertEquals("google@example.com", savedUser.getEmail());
        assertEquals(1, userRepository.count());
    }

    @Test
    void savesNaverUserUsingNaverId() {
        OAuth2User naverUser = oauth2User(
                Map.of("id", "naver-user-id", "email", "naver@example.com"),
                "id"
        );

        User savedUser = persistenceService.saveOrUpdate("naver", naverUser);

        assertEquals(OAuthProvider.NAVER, savedUser.getProvider());
        assertEquals("naver-user-id", savedUser.getProviderUserId());
        assertEquals("naver@example.com", savedUser.getEmail());
        assertEquals(1, userRepository.count());
    }

    @Test
    void updatesEmailWithoutCreatingDuplicateUser() {
        persistenceService.saveOrUpdate("naver", oauth2User(
                Map.of("id", "same-user-id", "email", "old@example.com"),
                "id"
        ));

        User updatedUser = persistenceService.saveOrUpdate("naver", oauth2User(
                Map.of("id", "same-user-id", "email", "new@example.com"),
                "id"
        ));

        assertEquals("new@example.com", updatedUser.getEmail());
        assertEquals(1, userRepository.count());
    }

    @Test
    void allowsUserWithoutEmail() {
        User savedUser = persistenceService.saveOrUpdate("naver", oauth2User(
                Map.of("id", "naver-user-without-email"),
                "id"
        ));

        assertNull(savedUser.getEmail());
        assertEquals(1, userRepository.count());
    }

    private OAuth2User oauth2User(Map<String, Object> attributes, String nameAttribute) {
        return new DefaultOAuth2User(
                Set.of(new SimpleGrantedAuthority("ROLE_USER")),
                attributes,
                nameAttribute
        );
    }
}
