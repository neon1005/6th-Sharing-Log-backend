package gdg.sharinglog.config.oauth;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

@Service
public class OAuth2UserCustomService extends DefaultOAuth2UserService {

    private static final String NAVER_REGISTRATION_ID = "naver";
    private static final String NAVER_RESPONSE_ATTRIBUTE = "response";
    private static final String NAVER_ID_ATTRIBUTE = "id";

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);
        String registrationId = userRequest.getClientRegistration().getRegistrationId();

        if (!NAVER_REGISTRATION_ID.equals(registrationId)) {
            return oAuth2User;
        }

        return normalizeNaverUser(oAuth2User);
    }

    static OAuth2User normalizeNaverUser(OAuth2User oAuth2User) {
        Object response = oAuth2User.getAttributes().get(NAVER_RESPONSE_ATTRIBUTE);
        if (!(response instanceof Map<?, ?> responseAttributes)) {
            throw invalidNaverResponse("네이버 사용자 정보 응답에 response 객체가 없습니다.");
        }

        Map<String, Object> profile = new LinkedHashMap<>();
        responseAttributes.forEach((key, value) -> {
            if (key instanceof String stringKey) {
                profile.put(stringKey, value);
            }
        });

        Object id = profile.get(NAVER_ID_ATTRIBUTE);
        if (id == null || id.toString().isBlank()) {
            throw invalidNaverResponse("네이버 사용자 정보 응답에 id가 없습니다.");
        }

        return new DefaultOAuth2User(oAuth2User.getAuthorities(), profile, NAVER_ID_ATTRIBUTE);
    }

    private static OAuth2AuthenticationException invalidNaverResponse(String description) {
        OAuth2Error error = new OAuth2Error("invalid_naver_user_info_response", description, null);
        return new OAuth2AuthenticationException(error, description);
    }
}
