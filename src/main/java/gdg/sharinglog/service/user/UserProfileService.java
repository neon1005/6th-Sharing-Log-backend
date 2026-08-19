package gdg.sharinglog.service.user;

import gdg.sharinglog.domain.User;
import gdg.sharinglog.web.dto.NotificationPreferencesResponse;
import gdg.sharinglog.web.dto.UserProfileResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final AuthenticatedUserService authenticatedUserService;

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(String registrationId, OAuth2User oAuth2User) {
        User user = authenticatedUserService.requireUser(registrationId, oAuth2User);
        return UserProfileResponse.from(user);
    }

    @Transactional
    public UserProfileResponse updateNickname(
            String registrationId,
            OAuth2User oAuth2User,
            String nickname
    ) {
        User user = authenticatedUserService.requireUserForUpdate(registrationId, oAuth2User);
        user.update(nickname);
        return UserProfileResponse.from(user);
    }

    @Transactional(readOnly = true)
    public NotificationPreferencesResponse getNotificationPreferences(
            String registrationId,
            OAuth2User oAuth2User
    ) {
        User user = authenticatedUserService.requireUser(registrationId, oAuth2User);
        return NotificationPreferencesResponse.from(user);
    }

    @Transactional
    public NotificationPreferencesResponse updateNotificationPreferences(
            String registrationId,
            OAuth2User oAuth2User,
            Boolean dueSoonEnabled,
            Boolean choreCompletedEnabled,
            Boolean noticeEnabled
    ) {
        User user = authenticatedUserService.requireUserForUpdate(registrationId, oAuth2User);
        user.updateNotificationPreferences(dueSoonEnabled, choreCompletedEnabled, noticeEnabled);
        return NotificationPreferencesResponse.from(user);
    }
}
