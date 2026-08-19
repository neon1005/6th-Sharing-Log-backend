package gdg.sharinglog.service.push;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;

import gdg.sharinglog.domain.OAuthProvider;
import gdg.sharinglog.domain.User;
import gdg.sharinglog.domain.push.PushSubscription;
import gdg.sharinglog.repository.UserRepository;
import gdg.sharinglog.repository.push.PushSubscriptionRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class PushNotifierTest {

    @Autowired
    PushNotifier pushNotifier;

    @Autowired
    PushSubscriptionRepository pushSubscriptionRepository;

    @Autowired
    UserRepository userRepository;

    @MockitoBean
    WebPushSender webPushSender;

    @Test
    void sendsToEverySubscriptionOfTheUser() {
        User user = userRepository.save(User.builder()
                .provider(OAuthProvider.GOOGLE)
                .providerUserId("push-notifier-user")
                .build());
        PushSubscription first = pushSubscriptionRepository.save(
                new PushSubscription(user, "https://push.example.com/1", "p1", "a1", Instant.now())
        );
        PushSubscription second = pushSubscriptionRepository.save(
                new PushSubscription(user, "https://push.example.com/2", "p2", "a2", Instant.now())
        );

        pushNotifier.notifyUser(user.getId(), "제목", "내용", "/notification");

        ArgumentCaptor<PushSubscription> captor = ArgumentCaptor.forClass(PushSubscription.class);
        verify(webPushSender, times(2)).send(captor.capture(), anyString());
        assertEquals(
                Set.of(first.getEndpoint(), second.getEndpoint()),
                captor.getAllValues().stream().map(PushSubscription::getEndpoint).collect(Collectors.toSet())
        );
    }

    @Test
    void doesNothingWhenUserHasNoSubscriptions() {
        pushNotifier.notifyUser(999_999L, "제목", "내용", "/notification");

        verifyNoInteractions(webPushSender);
    }
}
