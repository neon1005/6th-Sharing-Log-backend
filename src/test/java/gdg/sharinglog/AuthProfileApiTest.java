package gdg.sharinglog;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
class AuthProfileApiTest {

    private static final String GOOGLE_USER_ID = "profile-google-id";

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
                .email("profile@example.com")
                .nickname("원래닉네임")
                .build());
    }

    @Test
    void returnsCurrentUserProfile() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                        .with(oauth2Login()
                                .clientRegistration(googleClientRegistration())
                                .attributes(attributes -> attributes.put("sub", GOOGLE_USER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("profile@example.com"))
                .andExpect(jsonPath("$.nickname").value("원래닉네임"));
    }

    @Test
    void updatesNickname() throws Exception {
        mockMvc.perform(patch("/api/auth/me")
                        .with(csrf())
                        .with(oauth2Login()
                                .clientRegistration(googleClientRegistration())
                                .attributes(attributes -> attributes.put("sub", GOOGLE_USER_ID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nickname":"새 닉네임"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("새 닉네임"));

        User updated = userRepository.findById(user.getId()).orElseThrow();
        assertEquals("새 닉네임", updated.getNickname());
    }

    @Test
    void rejectsBlankNicknameWithoutChangingData() throws Exception {
        mockMvc.perform(patch("/api/auth/me")
                        .with(csrf())
                        .with(oauth2Login()
                                .clientRegistration(googleClientRegistration())
                                .attributes(attributes -> attributes.put("sub", GOOGLE_USER_ID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nickname":"   "}
                                """))
                .andExpect(status().isBadRequest());

        User unchanged = userRepository.findById(user.getId()).orElseThrow();
        assertEquals("원래닉네임", unchanged.getNickname());
    }

    @Test
    void requiresCsrfTokenForNicknameUpdate() throws Exception {
        mockMvc.perform(patch("/api/auth/me")
                        .with(oauth2Login()
                                .clientRegistration(googleClientRegistration())
                                .attributes(attributes -> attributes.put("sub", GOOGLE_USER_ID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nickname":"새 닉네임"}
                                """))
                .andExpect(status().isForbidden());

        User unchanged = userRepository.findById(user.getId()).orElseThrow();
        assertEquals("원래닉네임", unchanged.getNickname());
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
