package gdg.sharinglog.domain.rotation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import gdg.sharinglog.domain.GroupMember;
import gdg.sharinglog.domain.OAuthProvider;
import gdg.sharinglog.domain.SharingGroup;
import gdg.sharinglog.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RotationDecisionLogTest {

    private GroupMember member;
    private ChoreOccurrence occurrence;

    @BeforeEach
    void setUp() {
        User owner = User.builder()
                .provider(OAuthProvider.GOOGLE)
                .providerUserId("owner")
                .build();
        SharingGroup group = new SharingGroup("우리 집", owner);
        member = GroupMember.owner(group, owner);
        Chore chore = Chore.daily(
                group,
                member,
                "쓰레기 버리기",
                ChoreEligibilityMode.ALL_ACTIVE_MEMBERS,
                LocalTime.of(21, 0),
                Instant.parse("2026-07-23T00:00:00Z")
        );
        occurrence = ChoreOccurrence.create(
                chore,
                LocalDate.of(2026, 7, 23),
                LocalDate.of(2026, 7, 24),
                Instant.parse("2026-07-23T12:00:00Z"),
                Instant.parse("2026-07-22T15:00:00Z")
        );
    }

    @Test
    void assignedDecisionHasNoNoCandidateReason() {
        RotationDecisionLog decision = RotationDecisionLog.assigned(
                occurrence,
                1,
                AssignmentTrigger.INITIAL,
                member,
                "fair-random-v1",
                42L,
                "[{\"membershipId\":\"" + member.getPublicId() + "\"}]",
                "공정성 비교 후 선택",
                Instant.parse("2026-07-23T00:00:00Z")
        );

        assertEquals(RotationDecisionOutcome.ASSIGNED, decision.getOutcome());
        assertNull(decision.getNoCandidateReason());
    }

    @Test
    void noCandidateDecisionRequiresAndKeepsReason() {
        RotationDecisionLog decision = RotationDecisionLog.noCandidate(
                occurrence,
                1,
                AssignmentTrigger.INITIAL,
                NoCandidateReason.NO_ACTIVE_ELIGIBLE_NON_DECLINED_CANDIDATE,
                "fair-random-v1",
                42L,
                "[]",
                "유효한 후보 없음",
                Instant.parse("2026-07-23T00:00:00Z")
        );

        assertEquals(RotationDecisionOutcome.NO_CANDIDATE, decision.getOutcome());
        assertEquals(
                NoCandidateReason.NO_ACTIVE_ELIGIBLE_NON_DECLINED_CANDIDATE,
                decision.getNoCandidateReason()
        );
        assertNull(decision.getSelectedMember());
    }

    @Test
    void rejectsNoCandidateDecisionWithoutReason() {
        assertThrows(
                NullPointerException.class,
                () -> RotationDecisionLog.noCandidate(
                        occurrence,
                        1,
                        AssignmentTrigger.INITIAL,
                        null,
                        "fair-random-v1",
                        42L,
                        "[]",
                        "유효한 후보 없음",
                        Instant.parse("2026-07-23T00:00:00Z")
                )
        );
    }
}
