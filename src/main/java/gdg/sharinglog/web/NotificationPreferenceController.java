package gdg.sharinglog.web;

import gdg.sharinglog.service.user.UserProfileService;
import gdg.sharinglog.web.dto.NotificationPreferencesResponse;
import gdg.sharinglog.web.dto.UpdateNotificationPreferencesRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/api/notifications/preferences")
@RestController
@RequiredArgsConstructor
public class NotificationPreferenceController {

    private final UserProfileService userProfileService;

    @GetMapping
    public NotificationPreferencesResponse get(OAuth2AuthenticationToken authentication) {
        return userProfileService.getNotificationPreferences(
                authentication.getAuthorizedClientRegistrationId(),
                authentication.getPrincipal()
        );
    }

    @PatchMapping
    public NotificationPreferencesResponse update(
            @RequestBody UpdateNotificationPreferencesRequest request,
            OAuth2AuthenticationToken authentication
    ) {
        return userProfileService.updateNotificationPreferences(
                authentication.getAuthorizedClientRegistrationId(),
                authentication.getPrincipal(),
                request.dueSoonEnabled(),
                request.choreCompletedEnabled(),
                request.noticeEnabled()
        );
    }
}
