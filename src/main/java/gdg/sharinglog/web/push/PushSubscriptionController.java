package gdg.sharinglog.web.push;

import gdg.sharinglog.service.push.PushSubscriptionService;
import gdg.sharinglog.web.push.dto.SubscribePushRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/api/push/subscriptions")
@RestController
@RequiredArgsConstructor
public class PushSubscriptionController {

    private final PushSubscriptionService pushSubscriptionService;

    @PostMapping
    public ResponseEntity<Void> subscribe(
            @Valid @RequestBody SubscribePushRequest request,
            OAuth2AuthenticationToken authentication
    ) {
        pushSubscriptionService.subscribe(
                authentication.getAuthorizedClientRegistrationId(),
                authentication.getPrincipal(),
                request.endpoint(),
                request.p256dh(),
                request.auth()
        );
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> unsubscribe(
            @RequestParam String endpoint,
            OAuth2AuthenticationToken authentication
    ) {
        pushSubscriptionService.unsubscribe(
                authentication.getAuthorizedClientRegistrationId(),
                authentication.getPrincipal(),
                endpoint
        );
        return ResponseEntity.noContent().build();
    }
}
