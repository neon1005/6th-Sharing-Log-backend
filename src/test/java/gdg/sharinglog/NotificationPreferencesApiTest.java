package gdg.sharinglog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gdg.sharinglog.domain.OAuthProvider;
import gdg.sharinglog.domain.User;
import gdg.sharinglog.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class NotificationPreferencesApiTest {

    private static final String GOOGLE_USER_ID = "notification-prefs-google-id";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    private User user;

    @BeforeEach
    void saveUser() {
        user = userRepository.save(User.builder()
                .provider(OAuthProvider.GOOGLE)
                .providerUserId(GOOGLE_USER_ID)
                .email("prefs@example.com")
                .build());
    }

    @Test
    void returnsDefaultPreferences() throws Exception {
        mockMvc.perform(get("/api/notifications/preferences")
                        .with(oauth2Login()
                                .clientRegistration(googleClientRegistration())
                                .attributes(attributes -> attributes.put("sub", GOOGLE_USER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dueSoonEnabled").value(true))
                .andExpect(jsonPath("$.choreCompletedEnabled").value(true))
                .andExpect(jsonPath("$.noticeEnabled").value(false));
    }

    @Test
    void updatesOnlyTheGivenPreference() throws Exception {
        mockMvc.perform(patch("/api/notifications/preferences")
                        .with(csrf())
                        .with(oauth2Login()
                                .clientRegistration(googleClientRegistration())
                                .attributes(attributes -> attributes.put("sub", GOOGLE_USER_ID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"noticeEnabled":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dueSoonEnabled").value(true))
                .andExpect(jsonPath("$.choreCompletedEnabled").value(true))
                .andExpect(jsonPath("$.noticeEnabled").value(true));

        User updated = userRepository.findById(user.getId()).orElseThrow();
        assertTrue(updated.isDueSoonPushEnabled());
        assertTrue(updated.isChoreCompletedPushEnabled());
        assertTrue(updated.isNoticePushEnabled());
    }

    @Test
    void turnsOffDueSoonAlerts() throws Exception {
        mockMvc.perform(patch("/api/notifications/preferences")
                        .with(csrf())
                        .with(oauth2Login()
                                .clientRegistration(googleClientRegistration())
                                .attributes(attributes -> attributes.put("sub", GOOGLE_USER_ID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"dueSoonEnabled":false}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dueSoonEnabled").value(false));

        User updated = userRepository.findById(user.getId()).orElseThrow();
        assertFalse(updated.isDueSoonPushEnabled());
    }

    @Test
    void requiresCsrfTokenForUpdate() throws Exception {
        mockMvc.perform(patch("/api/notifications/preferences")
                        .with(oauth2Login()
                                .clientRegistration(googleClientRegistration())
                                .attributes(attributes -> attributes.put("sub", GOOGLE_USER_ID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"noticeEnabled":true}
                                """))
                .andExpect(status().isForbidden());

        User unchanged = userRepository.findById(user.getId()).orElseThrow();
        assertFalse(unchanged.isNoticePushEnabled());
    }

    private ClientRegistration googleClientRegistration() {
        return ClientRegistration.withRegistrationId("google")
                .clientId("test-google-client-id")
                .clientSecret("test-google-client-secret")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("openid", "profile", "email")
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .tokenUri("https://oauth2.googleapis.com/token")
                .userInfoUri("https://openidconnect.googleapis.com/v1/userinfo")
                .userNameAttributeName("sub")
                .clientName("Google")
                .build();
    }
}
