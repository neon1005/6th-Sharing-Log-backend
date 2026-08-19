package gdg.sharinglog.config.oauth;

import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

@Component
public class OAuth2SuccessHandler
        extends SavedRequestAwareAuthenticationSuccessHandler {

    private final OAuth2UserPersistenceService userPersistenceService;

    public OAuth2SuccessHandler(
            OAuth2UserPersistenceService userPersistenceService,
            @Value("${app.oauth2-success-url:}") String successUrl,
            @Value("${app.frontend-origin}") String frontendOrigin
    ) {
        this.userPersistenceService = userPersistenceService;
        setDefaultTargetUrl(OAuth2RedirectTargetResolver.resolve(
                successUrl,
                frontendOrigin,
                "/"
        ));
        // 초대 링크처럼 로그인 전에 저장된 요청이 있으면 해당 요청으로 돌아가고,
        // 저장된 요청이 없을 때만 기본 성공 URL을 사용한다.
        setAlwaysUseDefaultTargetUrl(false);
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException, ServletException {

        if (authentication instanceof OAuth2AuthenticationToken oAuth2Token) {
            userPersistenceService.saveOrUpdate(
                    oAuth2Token.getAuthorizedClientRegistrationId(),
                    oAuth2Token.getPrincipal()
            );
        }

        super.onAuthenticationSuccess(
                request,
                response,
                authentication
        );
    }
}
