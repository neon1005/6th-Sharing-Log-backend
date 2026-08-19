package gdg.sharinglog.service.push;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Security;

import gdg.sharinglog.domain.push.PushSubscription;
import lombok.extern.slf4j.Slf4j;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class WebPushSender {

    private final String vapidPublicKey;
    private final String vapidPrivateKey;
    private final String vapidSubject;

    private volatile PushService pushService;

    public WebPushSender(
            @Value("${app.push.vapid-public-key:}") String vapidPublicKey,
            @Value("${app.push.vapid-private-key:}") String vapidPrivateKey,
            @Value("${app.push.vapid-subject:mailto:noreply@example.com}") String vapidSubject
    ) {
        this.vapidPublicKey = vapidPublicKey;
        this.vapidPrivateKey = vapidPrivateKey;
        this.vapidSubject = vapidSubject;
        registerBouncyCastleProvider();
    }

    public void send(PushSubscription subscription, String payload) {
        try {
            PushService service = resolvePushService();
            if (service == null) {
                log.warn("VAPID 키가 설정되지 않아 푸시를 보내지 않습니다.");
                return;
            }
            Notification notification = new Notification(
                    subscription.getEndpoint(),
                    subscription.getP256dh(),
                    subscription.getAuth(),
                    payload.getBytes(StandardCharsets.UTF_8)
            );
            service.send(notification);
        } catch (Exception exception) {
            log.warn("푸시 발송 실패: endpoint={}", subscription.getEndpoint(), exception);
        }
    }

    private PushService resolvePushService() {
        if (vapidPublicKey.isBlank() || vapidPrivateKey.isBlank()) {
            return null;
        }
        PushService service = pushService;
        if (service == null) {
            synchronized (this) {
                service = pushService;
                if (service == null) {
                    try {
                        service = new PushService(vapidPublicKey, vapidPrivateKey, vapidSubject);
                        pushService = service;
                    } catch (GeneralSecurityException exception) {
                        log.error("VAPID 키 설정이 올바르지 않습니다.", exception);
                        return null;
                    }
                }
            }
        }
        return service;
    }

    private void registerBouncyCastleProvider() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }
}
