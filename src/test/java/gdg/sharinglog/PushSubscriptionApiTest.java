package gdg.sharinglog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gdg.sharinglog.domain.OAuthProvider;
import gdg.sharinglog.domain.User;
import gdg.sharinglog.domain.push.PushSubscription;
import gdg.sharinglog.repository.UserRepository;
import gdg.sharinglog.repository.push.PushSubscriptionRepository;
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
class PushSubscriptionApiTest {

    private static final String GOOGLE_USER_ID = "push-subscriber-google-id";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PushSubscriptionRepository pushSubscriptionRepository;

    private User user;

    @BeforeEach
    void saveUser() {
        user = userRepository.save(User.builder()
                .provider(OAuthProvider.GOOGLE)
                .providerUserId(GOOGLE_USER_ID)
                .email("subscriber@example.com")
                .build());
    }

    @Test
    void subscribesToPush() throws Exception {
        mockMvc.perform(post("/api/push/subscriptions")
                        .with(csrf())
                        .with(oauth2Login()
                                .clientRegistration(googleClientRegistration())
                                .attributes(attributes -> attributes.put("sub", GOOGLE_USER_ID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"endpoint":"https://push.example.com/abc","p256dh":"p-key","auth":"a-key"}
                                """))
                .andExpect(status().isNoContent());

        PushSubscription saved = pushSubscriptionRepository.findByEndpoint("https://push.example.com/abc")
                .orElseThrow();
        assertEquals(user.getId(), saved.getUser().getId());
        assertEquals("p-key", saved.getP256dh());
    }

    @Test
    void resubscribingWithSameEndpointUpdatesInsteadOfDuplicating() throws Exception {
        String body1 = """
                {"endpoint":"https://push.example.com/abc","p256dh":"p-key-1","auth":"a-key-1"}
                """;
        String body2 = """
                {"endpoint":"https://push.example.com/abc","p256dh":"p-key-2","auth":"a-key-2"}
                """;

        mockMvc.perform(post("/api/push/subscriptions")
                        .with(csrf())
                        .with(oauth2Login()
                                .clientRegistration(googleClientRegistration())
                                .attributes(attributes -> attributes.put("sub", GOOGLE_USER_ID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body1))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/push/subscriptions")
                        .with(csrf())
                        .with(oauth2Login()
                                .clientRegistration(googleClientRegistration())
                                .attributes(attributes -> attributes.put("sub", GOOGLE_USER_ID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body2))
                .andExpect(status().isNoContent());

        assertEquals(1, pushSubscriptionRepository.findAllByUser_Id(user.getId()).size());
        assertEquals(
                "p-key-2",
                pushSubscriptionRepository.findByEndpoint("https://push.example.com/abc").orElseThrow().getP256dh()
        );
    }

    @Test
    void unsubscribesFromPush() throws Exception {
        pushSubscriptionRepository.save(
                new PushSubscription(user, "https://push.example.com/xyz", "p", "a", java.time.Instant.now())
        );

        mockMvc.perform(delete("/api/push/subscriptions")
                        .with(csrf())
                        .with(oauth2Login()
                                .clientRegistration(googleClientRegistration())
                                .attributes(attributes -> attributes.put("sub", GOOGLE_USER_ID)))
                        .param("endpoint", "https://push.example.com/xyz"))
                .andExpect(status().isNoContent());

        assertTrue(pushSubscriptionRepository.findByEndpoint("https://push.example.com/xyz").isEmpty());
    }

    @Test
    void unsubscribingSomeoneElsesSubscriptionDoesNothing() throws Exception {
        User otherUser = userRepository.save(User.builder()
                .provider(OAuthProvider.GOOGLE)
                .providerUserId("push-other-google-id")
                .build());
        pushSubscriptionRepository.save(
                new PushSubscription(otherUser, "https://push.example.com/other", "p", "a", java.time.Instant.now())
        );

        mockMvc.perform(delete("/api/push/subscriptions")
                        .with(csrf())
                        .with(oauth2Login()
                                .clientRegistration(googleClientRegistration())
                                .attributes(attributes -> attributes.put("sub", GOOGLE_USER_ID)))
                        .param("endpoint", "https://push.example.com/other"))
                .andExpect(status().isNoContent());

        assertTrue(pushSubscriptionRepository.findByEndpoint("https://push.example.com/other").isPresent());
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
