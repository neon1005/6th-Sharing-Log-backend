package gdg.sharinglog.service.push;

import java.util.List;

import gdg.sharinglog.domain.push.PushSubscription;
import gdg.sharinglog.repository.push.PushSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class PushNotifier {

    private final PushSubscriptionRepository pushSubscriptionRepository;
    private final WebPushSender webPushSender;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public void notifyUser(Long userId, String title, String body, String url) {
        List<PushSubscription> subscriptions = pushSubscriptionRepository.findAllByUser_Id(userId);
        if (subscriptions.isEmpty()) {
            return;
        }
        String payload = buildPayload(title, body, url);
        for (PushSubscription subscription : subscriptions) {
            webPushSender.send(subscription, payload);
        }
    }

    private String buildPayload(String title, String body, String url) {
        return objectMapper.writeValueAsString(new PushPayload(title, body, url));
    }

    private record PushPayload(String title, String body, String url) {
    }
}
