package gdg.sharinglog.config.oauth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;

class OAuth2SuccessHandlerTest {

    private static final String SUCCESS_URL = "http://localhost:5173/house-choice";

    @Test
    void persistsAuthenticatedOAuth2UserBeforeRedirect() throws Exception {
        OAuth2UserPersistenceService persistenceService = mock(OAuth2UserPersistenceService.class);
        OAuth2SuccessHandler successHandler = new OAuth2SuccessHandler(
                persistenceService,
                SUCCESS_URL,
                "http://localhost:5173"
        );
        OAuth2User principal = new DefaultOAuth2User(
                Set.of(new SimpleGrantedAuthority("ROLE_USER")),
                Map.of("id", "naver-user-id", "email", "naver@example.com"),
                "id"
        );
        OAuth2AuthenticationToken authentication = new OAuth2AuthenticationToken(
                principal,
                principal.getAuthorities(),
                "naver"
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        successHandler.onAuthenticationSuccess(request, response, authentication);

        verify(persistenceService).saveOrUpdate("naver", principal);
        assertEquals(SUCCESS_URL, response.getRedirectedUrl());
    }

    @Test
    void redirectsToSavedInvitationRequestAfterLogin() throws Exception {
        OAuth2UserPersistenceService persistenceService = mock(OAuth2UserPersistenceService.class);
        OAuth2SuccessHandler successHandler = new OAuth2SuccessHandler(
                persistenceService,
                SUCCESS_URL,
                "http://localhost:5173"
        );
        OAuth2User principal = new DefaultOAuth2User(
                Set.of(new SimpleGrantedAuthority("ROLE_USER")),
                Map.of("sub", "google-user-id", "email", "google@example.com"),
                "sub"
        );
        OAuth2AuthenticationToken authentication = new OAuth2AuthenticationToken(
                principal,
                principal.getAuthorities(),
                "google"
        );
        MockHttpSession session = new MockHttpSession();
        MockHttpServletRequest invitationRequest = new MockHttpServletRequest(
                "GET", "/invite/AbCdEfGhIjKlMnOpQrStUv"
        );
        invitationRequest.setSession(session);
        new HttpSessionRequestCache().saveRequest(invitationRequest, new MockHttpServletResponse());

        MockHttpServletRequest callbackRequest = new MockHttpServletRequest();
        callbackRequest.setSession(session);
        MockHttpServletResponse response = new MockHttpServletResponse();

        successHandler.onAuthenticationSuccess(callbackRequest, response, authentication);

        verify(persistenceService).saveOrUpdate("google", principal);
        assertEquals(
                "http://localhost/invite/AbCdEfGhIjKlMnOpQrStUv?continue",
                response.getRedirectedUrl()
        );
    }

    @Test
    void redirectsToFrontendWhenSuccessUrlIsBlank() throws Exception {
        OAuth2UserPersistenceService persistenceService = mock(OAuth2UserPersistenceService.class);
        OAuth2SuccessHandler successHandler = new OAuth2SuccessHandler(
                persistenceService,
                "  ",
                "https://frontend.example/"
        );
        OAuth2User principal = new DefaultOAuth2User(
                Set.of(new SimpleGrantedAuthority("ROLE_USER")),
                Map.of("id", "naver-user-id"),
                "id"
        );
        OAuth2AuthenticationToken authentication = new OAuth2AuthenticationToken(
                principal,
                principal.getAuthorities(),
                "naver"
        );
        MockHttpServletResponse response = new MockHttpServletResponse();

        successHandler.onAuthenticationSuccess(
                new MockHttpServletRequest(),
                response,
                authentication
        );

        assertEquals("https://frontend.example/", response.getRedirectedUrl());
    }
}
