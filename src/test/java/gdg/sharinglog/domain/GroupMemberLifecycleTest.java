package gdg.sharinglog.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class GroupMemberLifecycleTest {

    @Test
    void leavesWithoutDeletingHistoricalIdentityAndCanReactivate() {
        User user = User.builder()
                .provider(OAuthProvider.GOOGLE)
                .providerUserId("member-lifecycle")
                .build();
        SharingGroup group = new SharingGroup("우리 집", user);
        GroupMember member = GroupMember.member(group, user);
        String publicId = member.getPublicId();
        long activationGeneration = member.getActivationGeneration();
        Instant leftAt = Instant.parse("2026-07-23T02:00:00Z");

        member.leave(leftAt);

        assertFalse(member.isActive());
        assertEquals(MemberStatus.LEFT, member.getStatus());
        assertEquals(leftAt, member.getLeftAt());
        assertEquals(publicId, member.getPublicId());

        Instant rejoinedAt = Instant.parse("2026-08-01T02:00:00Z");
        member.reactivate(rejoinedAt);

        assertTrue(member.isActive());
        assertEquals(MemberStatus.ACTIVE, member.getStatus());
        assertEquals(rejoinedAt, member.getJoinedAt());
        assertEquals(activationGeneration + 1, member.getActivationGeneration());
        assertNull(member.getLeftAt());
        assertNotNull(member.getPublicId());
    }

    @Test
    void rejectsRepeatedLeave() {
        User user = User.builder()
                .provider(OAuthProvider.GOOGLE)
                .providerUserId("member-repeated-leave")
                .build();
        GroupMember member = GroupMember.member(new SharingGroup("우리 집", user), user);
        member.leave(Instant.parse("2026-07-23T02:00:00Z"));

        assertThrows(
                IllegalStateException.class,
                () -> member.leave(Instant.parse("2026-07-24T02:00:00Z"))
        );
    }
}
