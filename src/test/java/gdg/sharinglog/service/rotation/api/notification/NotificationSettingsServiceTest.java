package gdg.sharinglog.service.rotation.api.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import gdg.sharinglog.domain.GroupMember;
import gdg.sharinglog.domain.OAuthProvider;
import gdg.sharinglog.domain.SharingGroup;
import gdg.sharinglog.domain.User;
import gdg.sharinglog.service.rotation.access.RotationActor;
import gdg.sharinglog.service.rotation.access.RotationActorAccessService;
import gdg.sharinglog.web.rotation.dto.UpdateNotificationSettingsRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.core.user.OAuth2User;

@ExtendWith(MockitoExtension.class)
class NotificationSettingsServiceTest {

    @Mock
    RotationActorAccessService accessService;

    @InjectMocks
    NotificationSettingsService service;

    @Test
    void updateChangesTheCurrentMembershipsFrequencySettings() {
        String groupPublicId = "group-public-id";
        OAuth2User principal = mock(OAuth2User.class);
        User user = User.builder()
                .provider(OAuthProvider.GOOGLE)
                .providerUserId("provider-user-id")
                .nickname("member")
                .build();
        SharingGroup group = new SharingGroup("group", user);
        GroupMember membership = GroupMember.member(group, user);
        RotationActor actor = new RotationActor(group, membership, user);

        when(accessService.requireActiveMemberForUpdate(
                groupPublicId,
                "google",
                principal
        )).thenReturn(actor);

        assertEquals(5, membership.getDailyDueSoonHours());
        assertEquals(5, membership.getWeeklyDueSoonHours());
        assertEquals(5, membership.getBiweeklyDueSoonHours());

        var response = service.update(
                groupPublicId,
                "google",
                principal,
                new UpdateNotificationSettingsRequest(4, 48, 240)
        );

        assertEquals(4, response.dailyHoursBeforeDue());
        assertEquals(48, response.weeklyHoursBeforeDue());
        assertEquals(240, response.biweeklyHoursBeforeDue());
    }
}
