package gdg.sharinglog.web.rotation;

import gdg.sharinglog.service.rotation.api.notification.NotificationSummaryService;
import gdg.sharinglog.web.rotation.dto.NotificationSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/groups/{groupId}/notifications")
@RequiredArgsConstructor
public class RotationNotificationController {

    private final NotificationSummaryService summaryService;

    @GetMapping("/summary")
    public NotificationSummaryResponse summary(
            @PathVariable String groupId,
            OAuth2AuthenticationToken authentication
    ) {
        return summaryService.summarize(
                groupId,
                authentication.getAuthorizedClientRegistrationId(),
                authentication.getPrincipal()
        );
    }
}
