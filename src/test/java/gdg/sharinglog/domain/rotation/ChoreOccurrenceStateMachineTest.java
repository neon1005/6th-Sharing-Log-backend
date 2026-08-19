package gdg.sharinglog.domain.rotation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import gdg.sharinglog.domain.GroupMember;
import gdg.sharinglog.domain.OAuthProvider;
import gdg.sharinglog.domain.SharingGroup;
import gdg.sharinglog.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ChoreOccurrenceStateMachineTest {

    private SharingGroup group;
    private GroupMember firstMember;
    private GroupMember secondMember;
    private Chore chore;
    private ChoreOccurrence occurrence;

    @BeforeEach
    void setUp() {
        User owner = user("owner");
        group = new SharingGroup("우리 집", owner);
        firstMember = GroupMember.owner(group, owner);
        secondMember = GroupMember.member(group, user("second"));
        chore = Chore.daily(
                group,
                firstMember,
                "쓰레기 버리기",
                ChoreEligibilityMode.ALL_ACTIVE_MEMBERS,
                LocalTime.of(21, 0),
                instant("2026-07-23T00:00:00Z")
        );
        occurrence = ChoreOccurrence.create(
                chore,
                LocalDate.of(2026, 7, 23),
                LocalDate.of(2026, 7, 24),
                instant("2026-07-23T12:00:00Z"),
                instant("2026-07-22T15:00:00Z")
        );
    }

    @Test
    void assignsAndCompletesWhileKeepingEndedAttemptAsHistory() {
        ChoreAssignmentAttempt assignment = assignment(firstMember, 1, AssignmentTrigger.INITIAL);
        occurrence.assign(assignment);

        occurrence.complete(instant("2026-07-23T11:00:00Z"));

        assertEquals(OccurrenceStatus.COMPLETED, occurrence.getStatus());
        assertTrue(occurrence.currentAssignee().isEmpty());
        assertEquals(AssignmentEndReason.COMPLETED, assignment.getEndReason());
        assertEquals(instant("2026-07-23T11:00:00Z"), assignment.getEndedAt());
        assertNull(assignment.getActiveMarker());
        assertFalse(assignment.isActive());
    }

    @Test
    void declineEndsOldAttemptAndAllowsImmediateReassignment() {
        ChoreAssignmentAttempt first = assignment(firstMember, 1, AssignmentTrigger.INITIAL);
        occurrence.assign(first);

        occurrence.releaseForReassignment(
                AssignmentEndReason.DECLINED_BY_ASSIGNEE,
                instant("2026-07-23T01:00:00Z")
        );

        assertEquals(OccurrenceStatus.NEEDS_ATTENTION, occurrence.getStatus());
        assertEquals(AssignmentEndReason.DECLINED_BY_ASSIGNEE, first.getEndReason());
        assertTrue(occurrence.currentAssignee().isEmpty());

        ChoreAssignmentAttempt replacement = assignment(
                secondMember,
                2,
                AssignmentTrigger.DECLINE_REASSIGNMENT
        );
        occurrence.assign(replacement);

        assertEquals(OccurrenceStatus.ASSIGNED, occurrence.getStatus());
        assertEquals(secondMember, occurrence.currentAssignee().orElseThrow());
        assertTrue(replacement.isActive());
    }

    @Test
    void skippedOccurrenceIsTerminalAndCannotBeReassigned() {
        ChoreAssignmentAttempt assignment = assignment(firstMember, 1, AssignmentTrigger.INITIAL);
        occurrence.assign(assignment);

        occurrence.skipAlreadyDone(instant("2026-07-23T02:00:00Z"));

        assertEquals(OccurrenceStatus.SKIPPED, occurrence.getStatus());
        assertEquals(AssignmentEndReason.SKIPPED_ALREADY_DONE, assignment.getEndReason());
        assertThrows(
                IllegalStateException.class,
                () -> occurrence.assign(assignment(secondMember, 2, AssignmentTrigger.NEEDS_ATTENTION_RETRY))
        );
    }

    @Test
    void onlyReassignmentReasonsCanReleaseCurrentAssignment() {
        occurrence.assign(assignment(firstMember, 1, AssignmentTrigger.INITIAL));

        assertThrows(
                IllegalArgumentException.class,
                () -> occurrence.releaseForReassignment(
                        AssignmentEndReason.COMPLETED,
                        instant("2026-07-23T02:00:00Z")
                )
        );
    }

    @Test
    void recordsNoCandidateAttentionAndPreservesOriginalSinceAcrossRetries() {
        Instant firstDecisionAt = instant("2026-07-22T16:00:00Z");
        Instant retryDecisionAt = instant("2026-07-22T17:00:00Z");

        assertNull(occurrence.getAttentionReason());
        assertNull(occurrence.getAttentionSince());
        assertNull(occurrence.getLastDecisionAt());

        occurrence.recordNoCandidate(
                NoCandidateReason.NO_ACTIVE_ELIGIBLE_NON_DECLINED_CANDIDATE,
                firstDecisionAt
        );
        occurrence.recordNoCandidate(
                NoCandidateReason.NO_ACTIVE_ELIGIBLE_NON_DECLINED_CANDIDATE,
                retryDecisionAt
        );

        assertEquals(
                NoCandidateReason.NO_ACTIVE_ELIGIBLE_NON_DECLINED_CANDIDATE,
                occurrence.getAttentionReason()
        );
        assertEquals(firstDecisionAt, occurrence.getAttentionSince());
        assertEquals(retryDecisionAt, occurrence.getLastDecisionAt());
        assertEquals(OccurrenceStatus.NEEDS_ATTENTION, occurrence.getStatus());
    }

    @Test
    void successfulAssignmentClearsAttentionAndRecordsLastDecisionTime() {
        occurrence.recordNoCandidate(
                NoCandidateReason.NO_ACTIVE_ELIGIBLE_NON_DECLINED_CANDIDATE,
                instant("2026-07-22T16:00:00Z")
        );
        ChoreAssignmentAttempt assignment =
                assignment(firstMember, 1, AssignmentTrigger.NEEDS_ATTENTION_RETRY);

        occurrence.assign(assignment);

        assertEquals(OccurrenceStatus.ASSIGNED, occurrence.getStatus());
        assertNull(occurrence.getAttentionReason());
        assertNull(occurrence.getAttentionSince());
        assertEquals(assignment.getAssignedAt(), occurrence.getLastDecisionAt());
    }

    @Test
    void rejectsNoCandidateDecisionUnlessOccurrenceNeedsAttention() {
        occurrence.assign(assignment(firstMember, 1, AssignmentTrigger.INITIAL));

        assertThrows(
                IllegalStateException.class,
                () -> occurrence.recordNoCandidate(
                        NoCandidateReason.NO_ACTIVE_ELIGIBLE_NON_DECLINED_CANDIDATE,
                        instant("2026-07-23T01:00:00Z")
                )
        );
    }

    @Test
    void rejectsDecisionOlderThanPreviousDecision() {
        occurrence.recordNoCandidate(
                NoCandidateReason.NO_ACTIVE_ELIGIBLE_NON_DECLINED_CANDIDATE,
                instant("2026-07-22T17:00:00Z")
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> occurrence.recordNoCandidate(
                        NoCandidateReason.NO_ACTIVE_ELIGIBLE_NON_DECLINED_CANDIDATE,
                        instant("2026-07-22T16:00:00Z")
                )
        );
    }

    @Test
    void rejectsPeriodLengthThatDoesNotMatchFrequency() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ChoreOccurrence.create(
                        chore,
                        LocalDate.of(2026, 7, 23),
                        LocalDate.of(2026, 8, 6),
                        instant("2026-07-23T12:00:00Z"),
                        instant("2026-07-22T15:00:00Z")
                )
        );
    }

    @Test
    void rejectsDueDateOutsideConfiguredDailyDate() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ChoreOccurrence.create(
                        chore,
                        LocalDate.of(2026, 7, 23),
                        LocalDate.of(2026, 7, 24),
                        instant("2026-07-24T12:00:00Z"),
                        instant("2026-07-22T15:00:00Z")
                )
        );
    }

    @Test
    void rejectsDifferentDueTimeEvenOnConfiguredDailyDate() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ChoreOccurrence.create(
                        chore,
                        LocalDate.of(2026, 7, 23),
                        LocalDate.of(2026, 7, 24),
                        instant("2026-07-23T11:00:00Z"),
                        instant("2026-07-22T15:00:00Z")
                )
        );
    }

    private ChoreAssignmentAttempt assignment(
            GroupMember member,
            int sequence,
            AssignmentTrigger trigger
    ) {
        return ChoreAssignmentAttempt.assigned(
                occurrence,
                member,
                sequence,
                trigger,
                instant("2026-07-23T00:00:00Z").plusSeconds(sequence),
                "fair-random-v1",
                42L,
                "[{\"membershipId\":\"" + member.getPublicId() + "\"}]",
                "공정성 비교 후 선택"
        );
    }

    private User user(String providerUserId) {
        return User.builder()
                .provider(OAuthProvider.GOOGLE)
                .providerUserId(providerUserId)
                .build();
    }

    private Instant instant(String value) {
        return Instant.parse(value);
    }
}
