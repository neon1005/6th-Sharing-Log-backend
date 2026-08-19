package gdg.sharinglog.config.oauth;

import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class OAuth2FailureHandler extends SimpleUrlAuthenticationFailureHandler {

    public OAuth2FailureHandler(
            @Value("${app.oauth2-failure-url:}") String failureUrl,
            @Value("${app.frontend-origin}") String frontendOrigin
    ) {
        setDefaultFailureUrl(OAuth2RedirectTargetResolver.resolve(
                failureUrl,
                frontendOrigin,
                "/?error=true"
        ));
    }

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException, ServletException {
        if (exception instanceof OAuth2AuthenticationException oAuth2Exception) {
            log.error(
                    "OAuth2 authentication failed: errorCode={}",
                    oAuth2Exception.getError().getErrorCode(),
                    exception
            );
        } else {
            log.error(
                    "OAuth2 authentication failed: exceptionType={}",
                    exception.getClass().getSimpleName(),
                    exception
            );
        }

        super.onAuthenticationFailure(request, response, exception);
    }
}
