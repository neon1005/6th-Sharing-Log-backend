package gdg.sharinglog.service.user;

import gdg.sharinglog.config.oauth.OAuth2UserIdentity;
import gdg.sharinglog.domain.User;
import gdg.sharinglog.repository.UserRepository;
import gdg.sharinglog.service.user.exception.AuthenticatedUserNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthenticatedUserService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public User requireUser(String registrationId, OAuth2User oAuth2User) {
        OAuth2UserIdentity identity = OAuth2UserIdentity.from(registrationId, oAuth2User);
        return userRepository
                .findByProviderAndProviderUserId(identity.provider(), identity.providerUserId())
                .orElseThrow(AuthenticatedUserNotFoundException::new);
    }

    @Transactional
    public User requireUserForUpdate(String registrationId, OAuth2User oAuth2User) {
        OAuth2UserIdentity identity = OAuth2UserIdentity.from(registrationId, oAuth2User);
        return userRepository
                .findByProviderAndProviderUserIdForUpdate(identity.provider(), identity.providerUserId())
                .orElseThrow(AuthenticatedUserNotFoundException::new);
    }
}
