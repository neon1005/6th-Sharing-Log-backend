package gdg.sharinglog.service.rotation.api.notification;

import java.util.Objects;

import gdg.sharinglog.domain.GroupMember;
import gdg.sharinglog.service.rotation.access.RotationActor;
import gdg.sharinglog.service.rotation.access.RotationActorAccessService;
import gdg.sharinglog.web.rotation.dto.NotificationSettingsResponse;
import gdg.sharinglog.web.rotation.dto.UpdateNotificationSettingsRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationSettingsService {

    private final RotationActorAccessService accessService;

    @Transactional(readOnly = true)
    public NotificationSettingsResponse find(
            String groupPublicId,
            String registrationId,
            OAuth2User principal
    ) {
        RotationActor actor =
                accessService.requireActiveMember(groupPublicId, registrationId, principal);
        return NotificationSettingsResponse.from(actor.membership());
    }

    @Transactional
    public NotificationSettingsResponse update(
            String groupPublicId,
            String registrationId,
            OAuth2User principal,
            UpdateNotificationSettingsRequest request
    ) {
        UpdateNotificationSettingsRequest requiredRequest =
                Objects.requireNonNull(request, "알림 설정 요청은 필수입니다.");
        requireAllHours(requiredRequest);
        RotationActor actor = accessService.requireActiveMemberForUpdate(
                groupPublicId,
                registrationId,
                principal
        );
        GroupMember membership = actor.membership();
        membership.updateDueSoonNotificationHours(
                requiredRequest.dailyHoursBeforeDue(),
                requiredRequest.weeklyHoursBeforeDue(),
                requiredRequest.biweeklyHoursBeforeDue()
        );
        return NotificationSettingsResponse.from(membership);
    }

    private void requireAllHours(UpdateNotificationSettingsRequest request) {
        if (request.dailyHoursBeforeDue() == null
                || request.weeklyHoursBeforeDue() == null
                || request.biweeklyHoursBeforeDue() == null) {
            throw new IllegalArgumentException("주기별 마감 임박 알림 시간은 모두 필수입니다.");
        }
    }
}
