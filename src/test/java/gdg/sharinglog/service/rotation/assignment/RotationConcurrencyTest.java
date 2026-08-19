package gdg.sharinglog.service.rotation.assignment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import gdg.sharinglog.domain.GroupMember;
import gdg.sharinglog.domain.MemberStatus;
import gdg.sharinglog.domain.OAuthProvider;
import gdg.sharinglog.domain.SharingGroup;
import gdg.sharinglog.domain.User;
import gdg.sharinglog.domain.rotation.AssignmentTrigger;
import gdg.sharinglog.domain.rotation.Chore;
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
import gdg.sharinglog.repository.rotation.RotationDecisionLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@Import(RotationConcurrencyTest.FixedSeedConfiguration.class)
class RotationConcurrencyTest {

    private static final Instant CREATED_AT = Instant.parse("2026-07-23T03:00:00Z");
    private static final Instant ACTION_AT = CREATED_AT.plusSeconds(60);
    private static final LocalDate PERIOD_START = LocalDate.of(2026, 7, 23);
    private static final LocalTime DUE_TIME = LocalTime.of(21, 0);

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    RotationAssignmentService assignmentService;

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
    OccurrenceEligibleMemberRepository eligibilityRepository;

    @Autowired
    ChoreAssignmentAttemptRepository assignmentRepository;

    @Autowired
    RotationDecisionLogRepository decisionLogRepository;

    private TransactionTemplate requiresNew;

    @BeforeEach
    void setUpTransactionTemplate() {
        requiresNew = new TransactionTemplate(transactionManager);
        requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        requiresNew.setTimeout(5);
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void sameGroupRowLockBlocksUntilCommit() throws Exception {
        Long groupId = createGroup("row-lock");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch firstLockAcquired = new CountDownLatch(1);
        CountDownLatch allowFirstCommit = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        CountDownLatch secondLockAcquired = new CountDownLatch(1);

        try {
            Future<?> first = executor.submit(() -> requiresNew.executeWithoutResult(status -> {
                groupRepository.findByIdForUpdate(groupId).orElseThrow();
                firstLockAcquired.countDown();
                await(allowFirstCommit, "첫 번째 그룹 잠금 해제 신호를 기다리지 못했습니다.");
            }));

            assertTrue(
                    firstLockAcquired.await(3, TimeUnit.SECONDS),
                    "첫 번째 트랜잭션이 그룹 행 잠금을 얻지 못했습니다."
            );

            Future<?> second = executor.submit(() -> {
                secondStarted.countDown();
                requiresNew.executeWithoutResult(status -> {
                    groupRepository.findByIdForUpdate(groupId).orElseThrow();
                    secondLockAcquired.countDown();
                });
            });

            assertTrue(
                    secondStarted.await(1, TimeUnit.SECONDS),
                    "두 번째 트랜잭션이 시작되지 않았습니다."
            );
            assertFalse(
                    secondLockAcquired.await(300, TimeUnit.MILLISECONDS),
                    "첫 번째 트랜잭션 커밋 전에 같은 그룹 잠금을 얻었습니다."
            );

            allowFirstCommit.countDown();
            first.get(3, TimeUnit.SECONDS);
            second.get(3, TimeUnit.SECONDS);
            assertEquals(0, secondLockAcquired.getCount());
        } finally {
            allowFirstCommit.countDown();
            shutdown(executor);
        }
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void assignmentWaitsForMemberLeaveAndNeverAssignsLeftMember() throws Exception {
        AssignmentFixture fixture = createAssignmentFixture("leave-race", 1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch leaveFlushed = new CountDownLatch(1);
        CountDownLatch allowLeaveCommit = new CountDownLatch(1);
        CountDownLatch assignmentStarted = new CountDownLatch(1);
        CountDownLatch assignmentFinished = new CountDownLatch(1);

        try {
            Future<?> leave = executor.submit(() -> requiresNew.executeWithoutResult(status -> {
                groupRepository.findByIdForUpdate(fixture.groupId()).orElseThrow();
                GroupMember member = groupMemberRepository
                        .findByPublicIdForUpdate(fixture.leavingMemberPublicId())
                        .orElseThrow();
                member.leave(ACTION_AT);
                groupMemberRepository.saveAndFlush(member);
                leaveFlushed.countDown();
                await(allowLeaveCommit, "탈퇴 트랜잭션 커밋 신호를 기다리지 못했습니다.");
            }));

            assertTrue(
                    leaveFlushed.await(3, TimeUnit.SECONDS),
                    "탈퇴 상태가 데이터베이스에 반영되지 않았습니다."
            );

            Future<?> assignment = executor.submit(() -> {
                assignmentStarted.countDown();
                assignmentService.assign(
                        fixture.occurrenceIds().getFirst(),
                        AssignmentTrigger.INITIAL,
                        ACTION_AT
                );
                assignmentFinished.countDown();
            });

            assertTrue(
                    assignmentStarted.await(1, TimeUnit.SECONDS),
                    "배정 트랜잭션이 시작되지 않았습니다."
            );
            assertFalse(
                    assignmentFinished.await(300, TimeUnit.MILLISECONDS),
                    "탈퇴 트랜잭션 커밋 전에 배정이 끝났습니다."
            );

            allowLeaveCommit.countDown();
            leave.get(3, TimeUnit.SECONDS);
            assignment.get(3, TimeUnit.SECONDS);

            requiresNew.executeWithoutResult(status -> {
                GroupMember leaving = groupMemberRepository
                        .findById(fixture.leavingMemberId())
                        .orElseThrow();
                ChoreOccurrence occurrence = occurrenceRepository
                        .findByIdForUpdate(fixture.occurrenceIds().getFirst())
                        .orElseThrow();

                assertEquals(MemberStatus.LEFT, leaving.getStatus());
                assertEquals(OccurrenceStatus.ASSIGNED, occurrence.getStatus());
                assertEquals(
                        fixture.remainingMemberId(),
                        occurrence.currentAssignee().orElseThrow().getId()
                );
                assertNotEquals(
                        fixture.leavingMemberId(),
                        occurrence.currentAssignee().orElseThrow().getId()
                );
            });
        } finally {
            allowLeaveCommit.countDown();
            shutdown(executor);
        }
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void sameGroupAssignmentsAreSerializedAndSplitPeriodLoad() throws Exception {
        AssignmentFixture fixture = createAssignmentFixture("assignment-race", 2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Future<?> first = submitAssignment(
                    executor,
                    fixture.occurrenceIds().get(0),
                    ready,
                    start
            );
            Future<?> second = submitAssignment(
                    executor,
                    fixture.occurrenceIds().get(1),
                    ready,
                    start
            );

            assertTrue(
                    ready.await(2, TimeUnit.SECONDS),
                    "두 배정 작업이 동시에 시작할 준비를 마치지 못했습니다."
            );
            start.countDown();

            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);

            requiresNew.executeWithoutResult(status -> {
                Long firstAssigneeId = assignmentRepository
                        .findFirstByOccurrence_IdAndEndedAtIsNull(
                                fixture.occurrenceIds().get(0)
                        )
                        .orElseThrow()
                        .getAssignee()
                        .getId();
                Long secondAssigneeId = assignmentRepository
                        .findFirstByOccurrence_IdAndEndedAtIsNull(
                                fixture.occurrenceIds().get(1)
                        )
                        .orElseThrow()
                        .getAssignee()
                        .getId();

                assertNotEquals(
                        firstAssigneeId,
                        secondAssigneeId,
                        "같은 기간의 동시 배정은 그룹 잠금 뒤 최신 부하를 반영해야 합니다."
                );
                assertEquals(
                        1,
                        assignmentRepository.countByOccurrence_Id(
                                fixture.occurrenceIds().get(0)
                        )
                );
                assertEquals(
                        1,
                        assignmentRepository.countByOccurrence_Id(
                                fixture.occurrenceIds().get(1)
                        )
                );
                assertEquals(
                        1,
                        decisionLogRepository.countByOccurrence_Id(
                                fixture.occurrenceIds().get(0)
                        )
                );
                assertEquals(
                        1,
                        decisionLogRepository.countByOccurrence_Id(
                                fixture.occurrenceIds().get(1)
                        )
                );
            });
        } finally {
            start.countDown();
            shutdown(executor);
        }
    }

    private Future<?> submitAssignment(
            ExecutorService executor,
            Long occurrenceId,
            CountDownLatch ready,
            CountDownLatch start
    ) {
        return executor.submit(() -> {
            ready.countDown();
            await(start, "동시 배정 시작 신호를 기다리지 못했습니다.");
            assignmentService.assign(occurrenceId, AssignmentTrigger.INITIAL, ACTION_AT);
        });
    }

    private Long createGroup(String suffix) {
        Long groupId = requiresNew.execute(status -> {
            User owner = userRepository.save(user("owner-" + suffix));
            SharingGroup group = groupRepository.save(new SharingGroup("group-" + suffix, owner));
            groupMemberRepository.save(GroupMember.owner(group, owner));
            return group.getId();
        });
        if (groupId == null) {
            throw new IllegalStateException("테스트 그룹을 생성하지 못했습니다.");
        }
        return groupId;
    }

    private AssignmentFixture createAssignmentFixture(String suffix, int occurrenceCount) {
        AssignmentFixture fixture = requiresNew.execute(status -> {
            User owner = userRepository.save(user("owner-" + suffix));
            User leavingUser = userRepository.save(user("leaving-" + suffix));
            SharingGroup group = groupRepository.save(new SharingGroup("group-" + suffix, owner));
            GroupMember remaining = groupMemberRepository.save(GroupMember.owner(group, owner));
            GroupMember leaving = groupMemberRepository.save(
                    GroupMember.member(group, leavingUser)
            );

            List<Long> occurrenceIds = java.util.stream.IntStream
                    .range(0, occurrenceCount)
                    .mapToObj(index -> createUnassignedOccurrence(
                            group,
                            remaining,
                            leaving,
                            suffix + "-" + index
                    ))
                    .toList();

            return new AssignmentFixture(
                    group.getId(),
                    occurrenceIds,
                    leaving.getId(),
                    leaving.getPublicId(),
                    remaining.getId()
            );
        });
        if (fixture == null) {
            throw new IllegalStateException("동시성 테스트 픽스처를 생성하지 못했습니다.");
        }
        return fixture;
    }

    private Long createUnassignedOccurrence(
            SharingGroup group,
            GroupMember firstMember,
            GroupMember secondMember,
            String suffix
    ) {
        Chore chore = choreRepository.save(Chore.daily(
                group,
                firstMember,
                "chore-" + suffix,
                ChoreEligibilityMode.ALL_ACTIVE_MEMBERS,
                DUE_TIME,
                CREATED_AT.minusSeconds(60)
        ));
        Instant dueAt = PERIOD_START
                .atTime(DUE_TIME)
                .atZone(ZoneId.of(group.getTimeZoneId()))
                .toInstant();
        ChoreOccurrence occurrence = occurrenceRepository.saveAndFlush(
                ChoreOccurrence.create(
                        chore,
                        PERIOD_START,
                        PERIOD_START.plusDays(1),
                        dueAt,
                        CREATED_AT
                )
        );
        eligibilityRepository.saveAllAndFlush(List.of(
                new OccurrenceEligibleMember(
                        occurrence,
                        occurrence.getEligibilitySnapshotVersion(),
                        firstMember,
                        CREATED_AT
                ),
                new OccurrenceEligibleMember(
                        occurrence,
                        occurrence.getEligibilitySnapshotVersion(),
                        secondMember,
                        CREATED_AT
                )
        ));
        return occurrence.getId();
    }

    private User user(String providerUserId) {
        return User.builder()
                .provider(OAuthProvider.GOOGLE)
                .providerUserId(providerUserId + "-" + UUID.randomUUID())
                .build();
    }

    private void await(CountDownLatch latch, String message) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError(message);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(message, exception);
        }
    }

    private void shutdown(ExecutorService executor) {
        executor.shutdownNow();
        try {
            executor.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private record AssignmentFixture(
            Long groupId,
            List<Long> occurrenceIds,
            Long leavingMemberId,
            String leavingMemberPublicId,
            Long remainingMemberId
    ) {
    }

    @TestConfiguration
    static class FixedSeedConfiguration {

        @Bean
        @Primary
        DecisionSeedGenerator fixedDecisionSeedGenerator() {
            return () -> 42L;
        }
    }
}
