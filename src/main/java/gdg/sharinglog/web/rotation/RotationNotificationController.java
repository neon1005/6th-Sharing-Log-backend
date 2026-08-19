package gdg.sharinglog.web.rotation;

import gdg.sharinglog.service.rotation.api.notification.NotificationSummaryService;
import gdg.sharinglog.service.rotation.api.notification.NotificationSettingsService;
import gdg.sharinglog.web.rotation.dto.NotificationSettingsResponse;
import gdg.sharinglog.web.rotation.dto.NotificationSummaryResponse;
import gdg.sharinglog.web.rotation.dto.UpdateNotificationSettingsRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/groups/{groupId}/notifications")
@RequiredArgsConstructor
public class RotationNotificationController {

    private final NotificationSummaryService summaryService;
    private final NotificationSettingsService settingsService;

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

    @GetMapping("/settings")
    public NotificationSettingsResponse settings(
            @PathVariable String groupId,
            OAuth2AuthenticationToken authentication
    ) {
        return settingsService.find(
                groupId,
                authentication.getAuthorizedClientRegistrationId(),
                authentication.getPrincipal()
        );
    }

    @PutMapping("/settings")
    public NotificationSettingsResponse updateSettings(
            @PathVariable String groupId,
            @Valid @RequestBody UpdateNotificationSettingsRequest request,
            OAuth2AuthenticationToken authentication
    ) {
        return settingsService.update(
                groupId,
                authentication.getAuthorizedClientRegistrationId(),
                authentication.getPrincipal(),
                request
        );
    }
}
