package gdg.sharinglog.service.push;

import java.time.Instant;

import gdg.sharinglog.domain.User;
import gdg.sharinglog.domain.push.PushSubscription;
import gdg.sharinglog.repository.push.PushSubscriptionRepository;
import gdg.sharinglog.service.user.AuthenticatedUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PushSubscriptionService {

    private final PushSubscriptionRepository pushSubscriptionRepository;
    private final AuthenticatedUserService authenticatedUserService;

    @Transactional
    public void subscribe(
            String registrationId,
            OAuth2User oAuth2User,
            String endpoint,
            String p256dh,
            String auth
    ) {
        User user = authenticatedUserService.requireUser(registrationId, oAuth2User);
        pushSubscriptionRepository.findByEndpoint(endpoint)
                .ifPresentOrElse(
                        existing -> existing.refresh(user, p256dh, auth),
                        () -> pushSubscriptionRepository.save(
                                new PushSubscription(user, endpoint, p256dh, auth, Instant.now())
                        )
                );
    }

    @Transactional
    public void unsubscribe(String registrationId, OAuth2User oAuth2User, String endpoint) {
        User user = authenticatedUserService.requireUser(registrationId, oAuth2User);
        pushSubscriptionRepository.deleteByEndpointAndUser_Id(endpoint, user.getId());
    }
}
