package gdg.sharinglog.config.oauth;

import org.springframework.util.StringUtils;

final class OAuth2RedirectTargetResolver {

    private OAuth2RedirectTargetResolver() {
    }

    static String resolve(
            String configuredUrl,
            String frontendOrigin,
            String defaultPathAndQuery
    ) {
        if (StringUtils.hasText(configuredUrl)) {
            return configuredUrl.strip();
        }
        if (!StringUtils.hasText(frontendOrigin)) {
            throw new IllegalArgumentException("프론트엔드 주소는 비어 있을 수 없습니다.");
        }

        String normalizedOrigin = frontendOrigin.strip().replaceFirst("/+$", "");
        return normalizedOrigin + defaultPathAndQuery;
    }
}
