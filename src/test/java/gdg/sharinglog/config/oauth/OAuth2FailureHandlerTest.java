package gdg.sharinglog.config.oauth;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationServiceException;

class OAuth2FailureHandlerTest {

    @Test
    void redirectsOAuth2FailureToFrontend() throws Exception {
        String failureUrl = "http://localhost:5173/?error=true";
        OAuth2FailureHandler failureHandler = new OAuth2FailureHandler(
                failureUrl,
                "http://localhost:5173"
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        failureHandler.onAuthenticationFailure(
                request,
                response,
                new AuthenticationServiceException("test failure")
        );

        assertEquals(failureUrl, response.getRedirectedUrl());
    }

    @Test
    void fallsBackToFrontendWhenFailureUrlIsBlank() throws Exception {
        OAuth2FailureHandler failureHandler = new OAuth2FailureHandler(
                "",
                "https://frontend.example/"
        );
        MockHttpServletResponse response = new MockHttpServletResponse();

        failureHandler.onAuthenticationFailure(
                new MockHttpServletRequest(),
                response,
                new AuthenticationServiceException("test failure")
        );

        assertEquals(
                "https://frontend.example/?error=true",
                response.getRedirectedUrl()
        );
    }
}
