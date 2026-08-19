package gdg.sharinglog;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "app.frontend-origin=http://localhost:5173/"
})
@AutoConfigureMockMvc
class LoginFlowTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void loginPageShowsOAuth2LoginButtons() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("구글로 로그인하기")))
                .andExpect(content().string(containsString("/oauth2/authorization/google")))
                .andExpect(content().string(containsString("네이버로 로그인하기")))
                .andExpect(content().string(containsString("/oauth2/authorization/naver")));
    }

    @Test
    void googleAuthorizationEndpointRedirectsToGoogle() throws Exception {
        mockMvc.perform(get("/oauth2/authorization/google"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location",
                        startsWith("https://accounts.google.com/o/oauth2/v2/auth?")));
    }

    @Test
    void naverAuthorizationEndpointRedirectsToNaver() throws Exception {
        mockMvc.perform(get("/oauth2/authorization/naver"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location",
                        startsWith("https://nid.naver.com/oauth2.0/authorize?")));
    }

    @Test
    void failedOAuth2CallbackRedirectsToFrontend() throws Exception {
        mockMvc.perform(get("/login/oauth2/code/google")
                        .param("error", "access_denied"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string(
                        "Location",
                        "http://localhost:5173/?error=true"
                ));
    }

    @Test
    void errorDispatchIsNotRedirectedToLogin() throws Exception {
        mockMvc.perform(get("/error"))
                .andExpect(header().doesNotExist("Location"));
    }

    @Test
    void allowsCredentialedRequestsFromFrontendOrigin() throws Exception {
        mockMvc.perform(options("/api/auth/csrf")
                        .header("Origin",
                                "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        "Access-Control-Allow-Origin",
                        "http://localhost:5173"
                ))
                .andExpect(header().string(
                        "Access-Control-Allow-Credentials",
                        "true"
                ));
    }

    @Test
    void rejectsApiCorsRequestFromUnknownOrigin() throws Exception {
        mockMvc.perform(options("/api/auth/csrf")
                        .header("Origin", "https://untrusted.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }

    @Test
    void invitationPageIsNotBlockedByApiCorsPolicy() throws Exception {
        mockMvc.perform(get("/invite/AbCdEfGhIjKlMnOpQrStUv")
                        .header("Origin", "https://untrusted.example"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", endsWith("/login")));
    }

    @Test
    void unauthenticatedApiRequestReturnsUnauthorizedWithoutLoginRedirect() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                        .header("Origin",
                                "http://localhost:5173"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(header().string(
                        "Access-Control-Allow-Origin",
                        "http://localhost:5173"
                ))
                .andExpect(header().string(
                        "Access-Control-Allow-Credentials",
                        "true"
                ));
    }

    @Test
    void homeShowsAuthenticatedGoogleUser() throws Exception {
        mockMvc.perform(get("/").with(oauth2Login()
                        .attributes(attributes -> {
                            attributes.put("name", "Test User");
                            attributes.put("email", "test@example.com");
                        })))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Test User님, 로그인되었습니다.")))
                .andExpect(content().string(containsString("test@example.com")))
                .andExpect(content().string(containsString("id=\"group-form\"")))
                .andExpect(content().string(containsString("id=\"group-address\"")))
                .andExpect(content().string(containsString("id=\"create-group-button\"")))
                .andExpect(content().string(containsString("id=\"join-invitation-form\"")))
                .andExpect(content().string(containsString("id=\"join-invitation-input\"")))
                .andExpect(content().string(containsString("id=\"accept-invitation-api-button\"")))
                .andExpect(content().string(containsString("id=\"join-invitation-result\"")))
                .andExpect(content().string(containsString("id=\"load-my-group-button\"")))
                .andExpect(content().string(containsString("id=\"my-group-result\"")))
                .andExpect(content().string(containsString("id=\"issue-invitation-button\"")))
                .andExpect(content().string(containsString(
                        "<input id=\"invite-url\" type=\"text\" readonly>")))
                .andExpect(content().string(containsString("id=\"invite-link\"")))
                .andExpect(content().string(containsString("id=\"members-form\"")))
                .andExpect(content().string(containsString("id=\"member-list-result\"")))
                .andExpect(content().string(containsString("/js/group-setup.js")))
                .andExpect(content().string(containsString("id=\"logout-button\"")))
                .andExpect(content().string(containsString("/js/logout.js")));
    }

    @Test
    void homeDoesNotShowGroupSetupToAnonymousUser() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        containsString("id=\"group-form\""))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        containsString("id=\"logout-button\""))));
    }

    @Test
    void groupSetupScriptIsPubliclyServed() throws Exception {
        mockMvc.perform(get("/js/group-setup.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/api/auth/csrf")))
                .andExpect(content().string(containsString("/api/groups")))
                .andExpect(content().string(containsString("/api/groups/me")))
                .andExpect(content().string(containsString("/api/invitations/")))
                .andExpect(content().string(containsString("/members")))
                .andExpect(content().string(containsString("request.address = groupAddress")))
                .andExpect(content().string(containsString(
                        "invitationUrl.origin !== window.location.origin")))
                .andExpect(content().string(containsString(
                        "window.location.assign(`/invite/${encodeURIComponent(code)}`)")));
    }

    @Test
    void authenticatedTemporaryPagesShowLogoutButton() throws Exception {
        mockMvc.perform(get("/booking-profile-check.html").with(oauth2Login()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"logout-button\"")))
                .andExpect(content().string(containsString("/js/logout.js")));

        mockMvc.perform(get("/rotation.html").with(oauth2Login()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"logout-button\"")))
                .andExpect(content().string(containsString("/js/logout.js")));
    }

    @Test
    void logoutScriptPostsToApiAndRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/js/logout.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/api/auth/logout")))
                .andExpect(content().string(containsString("method: \"POST\"")))
                .andExpect(content().string(containsString("credentials: \"include\"")))
                .andExpect(content().string(containsString(
                        "window.location.assign(\"/login\")")));
    }

    @Test
    void rotationNotificationCheckFrontendCallsAllNotificationApis() throws Exception {
        mockMvc.perform(get("/rotation.html").with(oauth2Login()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"open-notifications\"")))
                .andExpect(content().string(containsString("id=\"notifications-dialog\"")))
                .andExpect(content().string(containsString("id=\"notification-due-soon-list\"")))
                .andExpect(content().string(containsString("id=\"notification-substitute-list\"")));

        mockMvc.perform(get("/js/rotation.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/notifications/summary")))
                .andExpect(content().string(containsString("/occurrences/due-soon")))
                .andExpect(content().string(containsString(
                        "/substitute-requests?box=INBOX&status=PENDING")));
    }

    @Test
    void rotationOwnerCanReissueInvitationForExistingGroup() throws Exception {
        mockMvc.perform(get("/rotation.html").with(oauth2Login()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"reissue-invitation-button\"")))
                .andExpect(content().string(containsString("id=\"reissued-invitation-url\"")))
                .andExpect(content().string(containsString("id=\"reissued-invitation-link\"")))
                .andExpect(content().string(containsString("id=\"reissue-invitation-status\"")));

        mockMvc.perform(get("/js/rotation.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/invitations/reissue")))
                .andExpect(content().string(containsString("reissueInvitationButton.disabled = !canManage")));
    }

    @Test
    void rotationFrontendShowsNewOutboxRequestAndMovesToChangedScheduleTab() throws Exception {
        mockMvc.perform(get("/rotation.html").with(oauth2Login()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "<option value=\"OUTBOX\" selected>")));

        mockMvc.perform(get("/js/rotation.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "substituteBox.value = \"OUTBOX\"")))
                .andExpect(content().string(containsString(
                        "await openSubstituteRequests()")))
                .andExpect(content().string(containsString(
                        "activateFrequencyTab(selectedFrequency)")))
                .andExpect(content().string(containsString(
                        "scheduleDescription(chore.schedule)")));
    }

    // POST /api/auth/logout 호출 시 204가 오고 세션이 invalid 처리되는지 확인
    @Test
    void apiLogoutInvalidatesSessionAndReturnsNoContent() throws Exception {
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(post("/api/auth/logout")
                        .session(session)
                        .with(oauth2Login()))
                .andExpect(status().isNoContent());

        assertTrue(session.isInvalid());
    }
}
