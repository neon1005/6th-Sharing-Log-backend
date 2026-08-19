package gdg.sharinglog.rotation.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import gdg.sharinglog.domain.GroupMember;
import gdg.sharinglog.domain.OAuthProvider;
import gdg.sharinglog.domain.SharingGroup;
import gdg.sharinglog.domain.User;
import gdg.sharinglog.domain.rotation.AssignmentEndReason;
import gdg.sharinglog.domain.rotation.AssignmentTrigger;
import gdg.sharinglog.domain.rotation.Chore;
import gdg.sharinglog.domain.rotation.ChoreAssignmentAttempt;
import gdg.sharinglog.domain.rotation.ChoreEligibilityMode;
import gdg.sharinglog.domain.rotation.ChoreOccurrence;
import gdg.sharinglog.domain.rotation.OccurrenceEligibleMember;
import gdg.sharinglog.domain.rotation.OccurrenceStatus;
import gdg.sharinglog.repository.GroupMemberRepository;
import gdg.sharinglog.repository.SharingGroupRepository;
import gdg.sharinglog.repository.UserRepository;
import gdg.sharinglog.repository.rotation.ChoreAssignmentAttemptRepository;
import gdg.sharinglog.repository.rotation.ChoreOccurrenceRepository;
import gdg.sharinglog.repository.rotation.ChoreRepository;
import gdg.sharinglog.repository.rotation.OccurrenceEligibleMemberRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class RotationPersistenceTest {

    @Autowired
    UserRepository userRepository;

    @Autowired
    SharingGroupRepository groupRepository;

    @Autowired
    GroupMemberRepository groupMemberRepository;

    @Autowired
    ChoreRepository choreRepository;

    @Autowired
    ChoreOccurrenceRepository occurrenceRepository;

    @Autowired
    ChoreAssignmentAttemptRepository assignmentRepository;

    @Autowired
    OccurrenceEligibleMemberRepository eligibilityRepository;

    @Autowired
    EntityManager entityManager;

    private GroupMember ownerMembership;
    private Chore chore;

    @BeforeEach
    void setUp() {
        User owner = userRepository.save(User.builder()
                .provider(OAuthProvider.GOOGLE)
                .providerUserId("rotation-owner-" + System.nanoTime())
                .build());
        SharingGroup group = groupRepository.save(new SharingGroup("우리 집", owner));
        ownerMembership = groupMemberRepository.save(GroupMember.owner(group, owner));
        chore = choreRepository.save(Chore.daily(
                group,
                ownerMembership,
                "쓰레기 버리기",
                ChoreEligibilityMode.ALL_ACTIVE_MEMBERS,
                LocalTime.of(21, 0),
                Instant.parse("2026-07-23T00:00:00Z")
        ));
    }

    @Test
    void persistsOccurrenceEligibilityAssignmentAndCompletionHistory() {
        ChoreOccurrence occurrence = occurrenceRepository.save(occurrence());
        eligibilityRepository.save(new OccurrenceEligibleMember(
                occurrence,
                occurrence.getEligibilitySnapshotVersion(),
                ownerMembership,
                Instant.parse("2026-07-23T00:00:00Z")
        ));
        ChoreAssignmentAttempt assignment = assignmentRepository.save(assignment(occurrence, 1));
        occurrence.assign(assignment);
        entityManager.flush();
        entityManager.clear();

        ChoreOccurrence assigned = occurrenceRepository.findByPublicId(occurrence.getPublicId())
                .orElseThrow();

        assertEquals(OccurrenceStatus.ASSIGNED, assigned.getStatus());
        assertEquals(ownerMembership.getId(), assigned.currentAssignee().orElseThrow().getId());
        assertEquals(
                1,
                eligibilityRepository.findAllByOccurrence_IdAndSnapshotVersionOrderById(
                        assigned.getId(),
                        assigned.getEligibilitySnapshotVersion()
                ).size()
        );

        assigned.complete(Instant.parse("2026-07-23T10:00:00Z"));
        entityManager.flush();
        entityManager.clear();

        ChoreOccurrence completed = occurrenceRepository.findByPublicId(occurrence.getPublicId())
                .orElseThrow();
        ChoreAssignmentAttempt ended = assignmentRepository
                .findAllByOccurrence_IdOrderBySequenceNumber(completed.getId())
                .getFirst();

        assertEquals(OccurrenceStatus.COMPLETED, completed.getStatus());
        assertTrue(completed.currentAssignee().isEmpty());
        assertEquals(AssignmentEndReason.COMPLETED, ended.getEndReason());
        assertEquals(1, assignmentRepository.countCompletedForChoreAndMember(
                chore.getId(),
                ownerMembership.getId()
        ));
    }

    @Test
    void databaseRejectsDuplicateOccurrenceForSameChoreAndPeriod() {
        occurrenceRepository.saveAndFlush(occurrence());

        assertThrows(
                DataIntegrityViolationException.class,
                () -> occurrenceRepository.saveAndFlush(occurrence())
        );
    }

    @Test
    void databaseRejectsTwoActiveAssignmentAttemptsForOneOccurrence() {
        ChoreOccurrence occurrence = occurrenceRepository.saveAndFlush(occurrence());
        assignmentRepository.saveAndFlush(assignment(occurrence, 1));

        assertThrows(
                DataIntegrityViolationException.class,
                () -> assignmentRepository.saveAndFlush(assignment(occurrence, 2))
        );
    }

    @Test
    void validAssignmentCountsIncludeActiveAndEffectiveCompletionOnly() {
        assignedOccurrence(LocalDate.of(2026, 7, 23));

        ChoreOccurrence completed = assignedOccurrence(LocalDate.of(2026, 7, 24));
        completed.complete(Instant.parse("2026-07-24T10:00:00Z"));

        ChoreOccurrence revokedCompletion = assignedOccurrence(LocalDate.of(2026, 7, 25));
        revokedCompletion.complete(Instant.parse("2026-07-25T10:00:00Z"));
        ChoreAssignmentAttempt revokedAttempt = assignmentRepository
                .findAllByOccurrence_IdOrderBySequenceNumber(revokedCompletion.getId())
                .getFirst();
        revokedAttempt.revokeCompletion(
                ownerMembership,
                Instant.parse("2026-07-25T11:00:00Z"),
                "완료 취소"
        );

        ChoreOccurrence regenerated = assignedOccurrence(LocalDate.of(2026, 7, 26));
        regenerated.cancelForPlanRegeneration(Instant.parse("2026-07-26T11:00:00Z"));

        entityManager.flush();

        LocalDate targetPeriod = LocalDate.of(2026, 7, 27);
        assertEquals(2L, assignmentRepository
                .countValidAssignmentsForChoreAndMemberBeforePeriod(
                        chore.getId(),
                        targetPeriod,
                        ownerMembership.getId()
                ));
        assertEquals(2L, assignmentRepository
                .countValidAssignmentsForFrequencyAndMemberBeforePeriod(
                        chore.getGroup().getId(),
                        chore.getFrequency(),
                        targetPeriod,
                        ownerMembership.getId()
                ));
    }

    private ChoreOccurrence occurrence() {
        return occurrence(LocalDate.of(2026, 7, 23));
    }

    private ChoreOccurrence occurrence(LocalDate periodStart) {
        return ChoreOccurrence.create(
                chore,
                periodStart,
                periodStart.plusDays(1),
                periodStart.atTime(chore.getDueTime())
                        .atZone(chore.getGroup().timeZone())
                        .toInstant(),
                periodStart.minusDays(1)
                        .atStartOfDay(chore.getGroup().timeZone())
                        .toInstant()
        );
    }

    private ChoreOccurrence assignedOccurrence(LocalDate periodStart) {
        ChoreOccurrence occurrence = occurrenceRepository.save(occurrence(periodStart));
        ChoreAssignmentAttempt attempt = assignmentRepository.save(assignment(occurrence, 1));
        occurrence.assign(attempt);
        return occurrence;
    }

    private ChoreAssignmentAttempt assignment(ChoreOccurrence occurrence, int sequence) {
        return ChoreAssignmentAttempt.assigned(
                occurrence,
                ownerMembership,
                sequence,
                sequence == 1 ? AssignmentTrigger.INITIAL : AssignmentTrigger.NEEDS_ATTENTION_RETRY,
                occurrence.getPeriodStart()
                        .atStartOfDay(chore.getGroup().timeZone())
                        .toInstant()
                        .plusSeconds(sequence),
                "fair-random-v1",
                42L,
                "[{\"membershipId\":\"" + ownerMembership.getPublicId() + "\"}]",
                "공정성 비교 후 선택"
        );
    }
}
