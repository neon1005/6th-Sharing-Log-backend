package gdg.sharinglog.config.oauth;

import java.util.Locale;

import gdg.sharinglog.domain.User;
import gdg.sharinglog.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class OAuth2UserPersistenceService {

    private final UserRepository userRepository;

    @Transactional
    public User saveOrUpdate(String registrationId, OAuth2User oAuth2User) {
        OAuth2UserIdentity identity = OAuth2UserIdentity.from(registrationId, oAuth2User);
        String email = normalizedEmail(oAuth2User.getAttribute("email"));

        return userRepository.findByProviderAndProviderUserId(identity.provider(), identity.providerUserId())
                .map(user -> user.updateOAuthProfile(email))
                .orElseGet(() -> userRepository.save(User.builder()
                        .provider(identity.provider())
                        .providerUserId(identity.providerUserId())
                        .email(email)
                        .build()));
    }

    private String normalizedEmail(Object value) {
        if (value == null || !StringUtils.hasText(value.toString())) {
            return null;
        }
        return value.toString().trim().toLowerCase(Locale.ROOT);
    }
}
