package gdg.sharinglog.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.DayOfWeek;
import java.time.ZoneId;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class SharingGroupSchedulePolicyTest {

    @Test
    void usesKoreaAndMondayAsSafeDefaults() {
        SharingGroup group = new SharingGroup("우리 집", user("schedule-defaults"));

        assertEquals(ZoneId.of("Asia/Seoul"), group.timeZone());
        assertEquals(DayOfWeek.MONDAY, group.getWeekStartsOn());
        assertEquals(group.getPublicId(), UUID.fromString(group.getPublicId()).toString());
    }

    @Test
    void configuresGroupLocalScheduleBoundary() {
        SharingGroup group = new SharingGroup("밴쿠버 집", user("schedule-custom"));

        group.configureSchedulePolicy(ZoneId.of("America/Vancouver"), DayOfWeek.SUNDAY);

        assertEquals("America/Vancouver", group.getTimeZoneId());
        assertEquals(DayOfWeek.SUNDAY, group.getWeekStartsOn());
    }

    private User user(String providerUserId) {
        return User.builder()
                .provider(OAuthProvider.GOOGLE)
                .providerUserId(providerUserId)
                .build();
    }
}
