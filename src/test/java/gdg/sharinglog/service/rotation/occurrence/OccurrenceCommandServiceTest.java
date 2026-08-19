package gdg.sharinglog.service.rotation.occurrence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import gdg.sharinglog.domain.GroupMember;
import gdg.sharinglog.domain.MemberStatus;
import gdg.sharinglog.domain.OAuthProvider;
import gdg.sharinglog.domain.SharingGroup;
import gdg.sharinglog.domain.User;
import gdg.sharinglog.domain.rotation.AssignmentEndReason;
import gdg.sharinglog.domain.rotation.AssignmentTrigger;
import gdg.sharinglog.domain.rotation.Chore;
import gdg.sharinglog.domain.rotation.ChoreEligibleMember;
import gdg.sharinglog.domain.rotation.ChoreEligibilityMode;
import gdg.sharinglog.domain.rotation.ChoreOccurrence;
import gdg.sharinglog.domain.rotation.NoCandidateReason;
import gdg.sharinglog.domain.rotation.OccurrenceStatus;
import gdg.sharinglog.repository.GroupMemberRepository;
import gdg.sharinglog.repository.SharingGroupRepository;
import gdg.sharinglog.repository.UserRepository;
import gdg.sharinglog.repository.rotation.ChoreAssignmentAttemptRepository;
import gdg.sharinglog.repository.rotation.ChoreEligibleMemberRepository;
import gdg.sharinglog.repository.rotation.ChoreRepository;
import gdg.sharinglog.repository.rotation.RotationDecisionLogRepository;
import gdg.sharinglog.service.rotation.enrollment.ChoreEnrollmentService;
import gdg.sharinglog.service.rotation.exception.LastOwnerCannotLeaveException;
import gdg.sharinglog.service.rotation.exception.OccurrenceCommandConflictException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class OccurrenceCommandServiceTest {

    private static final Instant REFERENCE = Instant.parse("2026-07-23T03:00:00Z");
    private static final Instant ACTION_AT = Instant.parse("2026-07-23T04:00:00Z");

    @Autowired
    OccurrenceGenerationService generationService;

    @Autowired
    OccurrenceCommandService commandService;

    @Autowired
    ChoreEnrollmentService enrollmentService;

    @Autowired
    UserRepository userRepository;

    @Autowired
    SharingGroupRepository groupRepository;

    @Autowired
    GroupMemberRepository groupMemberRepository;

    @Autowired
    ChoreRepository choreRepository;

    @Autowired
    ChoreEligibleMemberRepository choreEligibleMemberRepository;

    @Autowired
    ChoreAssignmentAttemptRepository assignmentRepository;

    @Autowired
    RotationDecisionLogRepository decisionLogRepository;

    @Test
    void duplicateCompletionChangesStateAndFairnessCreditOnlyOnce() {
        Context context = context("complete");
        ChoreOccurrence occurrence = generate(context);
        GroupMember assignee = occurrence.currentAssignee().orElseThrow();

        commandService.complete(occurrence.getPublicId(), assignee.getPublicId(), ACTION_AT);
        commandService.complete(
                occurrence.getPublicId(),
                assignee.getPublicId(),
                ACTION_AT.plusSeconds(30)
        );

        assertEquals(OccurrenceStatus.COMPLETED, occurrence.getStatus());
        assertEquals(1, assignmentRepository.countByOccurrence_Id(occurrence.getId()));
        assertEquals(
                1,
                assignmentRepository.countCompletedForChoreAndMember(
                        occurrence.getChore().getId(),
                        assignee.getId()
                )
        );
    }

    @Test
    void undoCompletionReopensForSameMemberAndRemovesCompletionCredit() {
        Context context = context("undo-complete");
        ChoreOccurrence occurrence = generate(context);
        GroupMember assignee = occurrence.currentAssignee().orElseThrow();

        commandService.complete(occurrence.getPublicId(), assignee.getPublicId(), ACTION_AT);
        commandService.undoCompletion(
                occurrence.getPublicId(),
                assignee.getPublicId(),
                ACTION_AT.plusSeconds(30),
                "완료 버튼을 잘못 눌렀어요."
        );

        var attempts = assignmentRepository
                .findAllByOccurrence_IdOrderBySequenceNumber(occurrence.getId());
        assertEquals(OccurrenceStatus.ASSIGNED, occurrence.getStatus());
        assertEquals(assignee.getId(), occurrence.currentAssignee().orElseThrow().getId());
        assertEquals(2, attempts.size());
        assertNotNull(attempts.getFirst().getCompletionRevokedAt());
        assertEquals(
                AssignmentTrigger.COMPLETION_REOPENED,
                attempts.getLast().getTrigger()
        );
        assertEquals(
                0,
                assignmentRepository.countCompletedForChoreAndMember(
                        occurrence.getChore().getId(),
                        assignee.getId()
                )
        );
    }

    @Test
    void skipClosesOccurrenceWithoutCompletionCredit() {
        Context context = context("skip");
        ChoreOccurrence occurrence = generate(context);
        GroupMember assignee = occurrence.currentAssignee().orElseThrow();

        commandService.skipAlreadyDone(
                occurrence.getPublicId(),
                assignee.getPublicId(),
                ACTION_AT
        );

        assertEquals(OccurrenceStatus.SKIPPED, occurrence.getStatus());
        assertEquals(
                0,
                assignmentRepository.countCompletedForChoreAndMember(
                        occurrence.getChore().getId(),
                        assignee.getId()
                )
        );
        assertEquals(
                AssignmentEndReason.SKIPPED_ALREADY_DONE,
                assignmentRepository
                        .findAllByOccurrence_IdOrderBySequenceNumber(occurrence.getId())
                        .getFirst()
                        .getEndReason()
        );
    }

    @Test
    void declineImmediatelyReassignsAndDuplicateDeclineIsIdempotent() {
        Context context = context("decline");
        ChoreOccurrence occurrence = generate(context);
        GroupMember firstAssignee = occurrence.currentAssignee().orElseThrow();

        commandService.declineCurrentOccurrence(
                occurrence.getPublicId(),
                firstAssignee.getPublicId(),
                ACTION_AT
        );
        GroupMember replacement = occurrence.currentAssignee().orElseThrow();
        commandService.declineCurrentOccurrence(
                occurrence.getPublicId(),
                firstAssignee.getPublicId(),
                ACTION_AT.plusSeconds(30)
        );

        assertNotEquals(firstAssignee.getId(), replacement.getId());
        assertEquals(OccurrenceStatus.ASSIGNED, occurrence.getStatus());
        assertEquals(2, assignmentRepository.countByOccurrence_Id(occurrence.getId()));
        assertEquals(2, decisionLogRepository.countByOccurrence_Id(occurrence.getId()));
        assertEquals(
                AssignmentEndReason.DECLINED_BY_ASSIGNEE,
                assignmentRepository
                        .findAllByOccurrence_IdOrderBySequenceNumber(occurrence.getId())
                        .getFirst()
                        .getEndReason()
        );
    }

    @Test
    void allMembersDecliningMovesOccurrenceToNeedsAttention() {
        Context context = context("all-decline");
        ChoreOccurrence occurrence = generate(context);
        GroupMember first = occurrence.currentAssignee().orElseThrow();
        commandService.declineCurrentOccurrence(
                occurrence.getPublicId(),
                first.getPublicId(),
                ACTION_AT
        );
        GroupMember second = occurrence.currentAssignee().orElseThrow();

        commandService.declineCurrentOccurrence(
                occurrence.getPublicId(),
                second.getPublicId(),
                ACTION_AT.plusSeconds(30)
        );

        assertEquals(OccurrenceStatus.NEEDS_ATTENTION, occurrence.getStatus());
        assertTrue(occurrence.currentAssignee().isEmpty());
        assertEquals(2, assignmentRepository.countByOccurrence_Id(occurrence.getId()));
        assertEquals(3, decisionLogRepository.countByOccurrence_Id(occurrence.getId()));
        assertEquals(
                "NO_CANDIDATE",
                decisionLogRepository
                        .findAllByOccurrence_IdOrderByDecisionSequence(occurrence.getId())
                        .getLast()
                        .getOutcome()
                        .name()
        );
        assertEquals(
                NoCandidateReason.NO_ACTIVE_ELIGIBLE_NON_DECLINED_CANDIDATE,
                occurrence.getAttentionReason()
        );
        assertEquals(ACTION_AT.plusSeconds(30), occurrence.getAttentionSince());
        assertEquals(ACTION_AT.plusSeconds(30), occurrence.getLastDecisionAt());
    }

    @Test
    void leavingAssigneeIsSoftDeletedAndOpenOccurrenceIsReassigned() {
        Context context = context("leave");
        addBackupOwner(context, "leave");
        ChoreOccurrence occurrence = generate(context);
        GroupMember leaving = occurrence.currentAssignee().orElseThrow();

        List<ChoreOccurrence> affected = commandService.leaveMember(
                leaving.getPublicId(),
                ACTION_AT
        );

        assertEquals(List.of(occurrence), affected);
        assertEquals(MemberStatus.LEFT, leaving.getStatus());
        assertEquals(OccurrenceStatus.ASSIGNED, occurrence.getStatus());
        assertNotEquals(leaving.getId(), occurrence.currentAssignee().orElseThrow().getId());
        assertEquals(
                AssignmentEndReason.ASSIGNEE_LEFT_GROUP,
                assignmentRepository
                        .findAllByOccurrence_IdOrderBySequenceNumber(occurrence.getId())
                        .getFirst()
                        .getEndReason()
        );
    }

    @Test
    void leavingAfterCompletionDoesNotRewriteClosedOccurrenceHistory() {
        Context context = context("leave-completed");
        addBackupOwner(context, "leave-completed");
        ChoreOccurrence occurrence = generate(context);
        GroupMember assignee = occurrence.currentAssignee().orElseThrow();
        commandService.complete(occurrence.getPublicId(), assignee.getPublicId(), ACTION_AT);

        List<ChoreOccurrence> affected = commandService.leaveMember(
                assignee.getPublicId(),
                ACTION_AT.plusSeconds(30)
        );

        assertTrue(affected.isEmpty());
        assertEquals(OccurrenceStatus.COMPLETED, occurrence.getStatus());
        assertEquals(
                AssignmentEndReason.COMPLETED,
                assignmentRepository
                        .findAllByOccurrence_IdOrderBySequenceNumber(occurrence.getId())
                        .getFirst()
                        .getEndReason()
        );
    }

    @Test
    void leavingOnlyEligibleAssigneeCreatesNeedsAttention() {
        Context context = context("leave-no-candidate");
        GroupMember onlyEligible = context.members().getLast();
        Chore chore = choreRepository.save(Chore.daily(
                context.group(),
                context.members().getFirst(),
                "가스 점검",
                ChoreEligibilityMode.SELECTED_MEMBERS,
                LocalTime.of(21, 0),
                REFERENCE.minusSeconds(60)
        ));
        choreEligibleMemberRepository.save(new ChoreEligibleMember(chore, onlyEligible));
        ChoreOccurrence occurrence =
                generationService.ensureCurrentOccurrence(chore.getId(), REFERENCE);

        commandService.leaveMember(onlyEligible.getPublicId(), ACTION_AT);

        assertEquals(MemberStatus.LEFT, onlyEligible.getStatus());
        assertEquals(OccurrenceStatus.NEEDS_ATTENTION, occurrence.getStatus());
        assertTrue(occurrence.currentAssignee().isEmpty());
        assertEquals(
                NoCandidateReason.NO_ACTIVE_ELIGIBLE_NON_DECLINED_CANDIDATE,
                occurrence.getAttentionReason()
        );
        assertEquals(ACTION_AT, occurrence.getAttentionSince());
        assertEquals(
                AssignmentEndReason.ASSIGNEE_LEFT_GROUP,
                assignmentRepository
                        .findAllByOccurrence_IdOrderBySequenceNumber(occurrence.getId())
                        .getFirst()
                        .getEndReason()
        );
    }

    @Test
    void rejoinedMemberWaitsBehindExistingMembersAndCannotReenterOldOccurrence() {
        Context context = context("rejoin-tail");
        addBackupOwner(context, "rejoin-tail");
        ChoreOccurrence occurrence = generate(context);
        GroupMember rejoining = occurrence.currentAssignee().orElseThrow();

        commandService.leaveMember(rejoining.getPublicId(), ACTION_AT);
        GroupMember replacement = occurrence.currentAssignee().orElseThrow();

        Instant rejoinedAt = ACTION_AT.plusSeconds(10);
        rejoining.reactivate(rejoinedAt);
        groupMemberRepository.saveAndFlush(rejoining);
        enrollmentService.activateMemberEnrollments(rejoining, rejoinedAt);

        commandService.declineCurrentOccurrence(
                occurrence.getPublicId(),
                replacement.getPublicId(),
                ACTION_AT.plusSeconds(20)
        );

        assertNotEquals(
                rejoining.getId(),
                occurrence.currentAssignee().orElseThrow().getId()
        );

        ChoreOccurrence next = generationService.ensureCurrentOccurrence(
                occurrence.getChore().getId(),
                REFERENCE.plusSeconds(86_400)
        );
        assertNotEquals(rejoining.getId(), next.currentAssignee().orElseThrow().getId());
    }

    @Test
    void lastOwnerCannotLeaveWhenAnotherActiveMemberRemains() {
        Context context = context("last-owner");
        GroupMember owner = context.members().getFirst();

        assertThrows(
                LastOwnerCannotLeaveException.class,
                () -> commandService.leaveMember(owner.getPublicId(), ACTION_AT)
        );

        assertEquals(MemberStatus.ACTIVE, owner.getStatus());
    }

    @Test
    void futureOccurrenceCannotBeCompleted() {
        Context context = context("future-complete");
        Chore chore = choreRepository.save(Chore.daily(
                context.group(),
                context.members().getFirst(),
                "미래 청소",
                ChoreEligibilityMode.ALL_ACTIVE_MEMBERS,
                LocalTime.of(21, 0),
                REFERENCE.minusSeconds(60)
        ));
        List<ChoreOccurrence> occurrences = generationService.ensureOccurrencesUntil(
                chore.getId(),
                REFERENCE,
                LocalDate.of(2026, 7, 25)
        );
        ChoreOccurrence future = occurrences.stream()
                .filter(item -> item.getPeriodStart().equals(LocalDate.of(2026, 7, 24)))
                .findFirst()
                .orElseThrow();
        GroupMember assignee = future.currentAssignee().orElseThrow();

        assertThrows(
                OccurrenceCommandConflictException.class,
                () -> commandService.complete(
                        future.getPublicId(),
                        assignee.getPublicId(),
                        ACTION_AT
                )
        );
        assertEquals(OccurrenceStatus.ASSIGNED, future.getStatus());
        assertTrue(assignmentRepository
                .findFirstByOccurrence_IdAndEndedAtIsNull(future.getId())
                .isPresent());
    }

    @Test
    void soleOwnerCanLeaveGroup() {
        User owner = userRepository.save(user("sole-owner"));
        SharingGroup group = groupRepository.save(new SharingGroup("혼자 사는 집", owner));
        GroupMember membership = groupMemberRepository.save(GroupMember.owner(group, owner));

        List<ChoreOccurrence> affected = commandService.leaveMember(
                membership.getPublicId(),
                ACTION_AT
        );

        assertTrue(affected.isEmpty());
        assertEquals(MemberStatus.LEFT, membership.getStatus());
        assertEquals(ACTION_AT, membership.getLeftAt());
    }

    @Test
    void nonAssigneeCannotCompleteOccurrence() {
        Context context = context("non-assignee");
        ChoreOccurrence occurrence = generate(context);
        GroupMember assignee = occurrence.currentAssignee().orElseThrow();
        GroupMember other = context.members().stream()
                .filter(member -> !member.getId().equals(assignee.getId()))
                .findFirst()
                .orElseThrow();

        assertThrows(
                OccurrenceCommandConflictException.class,
                () -> commandService.complete(
                        occurrence.getPublicId(),
                        other.getPublicId(),
                        ACTION_AT
                )
        );
        assertEquals(OccurrenceStatus.ASSIGNED, occurrence.getStatus());
    }

    @Test
    void otherMemberCannotReplayAssigneesCompletedCommand() {
        Context context = context("completed-replay");
        ChoreOccurrence occurrence = generate(context);
        GroupMember assignee = occurrence.currentAssignee().orElseThrow();
        GroupMember other = context.members().stream()
                .filter(member -> !member.getId().equals(assignee.getId()))
                .findFirst()
                .orElseThrow();
        commandService.complete(occurrence.getPublicId(), assignee.getPublicId(), ACTION_AT);

        assertThrows(
                OccurrenceCommandConflictException.class,
                () -> commandService.complete(
                        occurrence.getPublicId(),
                        other.getPublicId(),
                        ACTION_AT.plusSeconds(30)
                )
        );
        assertEquals(OccurrenceStatus.COMPLETED, occurrence.getStatus());
    }

    private ChoreOccurrence generate(Context context) {
        Chore chore = choreRepository.save(Chore.daily(
                context.group(),
                context.members().getFirst(),
                "쓰레기 버리기",
                ChoreEligibilityMode.ALL_ACTIVE_MEMBERS,
                LocalTime.of(21, 0),
                REFERENCE.minusSeconds(60)
        ));
        return generationService.ensureCurrentOccurrence(chore.getId(), REFERENCE);
    }

    private Context context(String suffix) {
        User owner = userRepository.save(user("owner-" + suffix));
        User member = userRepository.save(user("member-" + suffix));
        SharingGroup group = groupRepository.save(new SharingGroup("우리 집", owner));
        GroupMember ownerMembership = groupMemberRepository.save(GroupMember.owner(group, owner));
        GroupMember membership = groupMemberRepository.save(GroupMember.member(group, member));
        return new Context(group, List.of(ownerMembership, membership));
    }

    private void addBackupOwner(Context context, String suffix) {
        User backupOwner = userRepository.save(user("backup-owner-" + suffix));
        groupMemberRepository.save(GroupMember.owner(context.group(), backupOwner));
    }

    private User user(String providerUserId) {
        return User.builder()
                .provider(OAuthProvider.GOOGLE)
                .providerUserId(providerUserId + "-" + System.nanoTime())
                .build();
    }

    private record Context(SharingGroup group, List<GroupMember> members) {
    }
}
