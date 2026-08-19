package gdg.sharinglog.service.rotation.occurrence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import gdg.sharinglog.domain.GroupMember;
import gdg.sharinglog.domain.OAuthProvider;
import gdg.sharinglog.domain.SharingGroup;
import gdg.sharinglog.domain.User;
import gdg.sharinglog.domain.rotation.Chore;
import gdg.sharinglog.domain.rotation.ChoreEligibleMember;
import gdg.sharinglog.domain.rotation.ChoreEligibilityMode;
import gdg.sharinglog.domain.rotation.ChoreFrequency;
import gdg.sharinglog.domain.rotation.ChoreOccurrence;
import gdg.sharinglog.domain.rotation.AssignmentEndReason;
import gdg.sharinglog.domain.rotation.NoCandidateReason;
import gdg.sharinglog.domain.rotation.OccurrenceStatus;
import gdg.sharinglog.repository.GroupMemberRepository;
import gdg.sharinglog.repository.SharingGroupRepository;
import gdg.sharinglog.repository.UserRepository;
import gdg.sharinglog.repository.rotation.ChoreAssignmentAttemptRepository;
import gdg.sharinglog.repository.rotation.ChoreEligibleMemberRepository;
import gdg.sharinglog.repository.rotation.ChoreOccurrenceRepository;
import gdg.sharinglog.repository.rotation.ChoreRepository;
import gdg.sharinglog.repository.rotation.RotationDecisionLogRepository;
import gdg.sharinglog.service.rotation.assignment.RotationAssignmentService;
import gdg.sharinglog.service.rotation.enrollment.ChoreEnrollmentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class OccurrenceGenerationServiceTest {

    @Autowired
    OccurrenceGenerationService generationService;

    @Autowired
    OccurrencePlanService planService;

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
    ChoreOccurrenceRepository occurrenceRepository;

    @Autowired
    ChoreAssignmentAttemptRepository assignmentRepository;

    @Autowired
    RotationDecisionLogRepository decisionLogRepository;

    @Test
    void assignsEveryParticipantOnceBeforeStartingTheNextChoreCycle() {
        Context context = context("assignment-cycle");
        GroupMember second = addMember(context, "cycle-second");
        GroupMember third = addMember(context, "cycle-third");
        Instant generatedAt = Instant.parse("2026-08-03T03:00:00Z");
        Chore chore = choreRepository.save(Chore.daily(
                context.group(),
                context.ownerMembership(),
                "매일 공용 청소",
                ChoreEligibilityMode.ALL_ACTIVE_MEMBERS,
                LocalTime.of(20, 0),
                generatedAt.minusSeconds(60)
        ));

        List<Long> assigneeIds = generationService.ensureOccurrencesUntil(
                        chore.getId(),
                        generatedAt,
                        LocalDate.of(2026, 8, 10)
                )
                .stream()
                .map(occurrence -> occurrence.currentAssignee().orElseThrow().getId())
                .toList();
        Set<Long> participants = Set.of(
                context.ownerMembership().getId(),
                second.getId(),
                third.getId()
        );

        assertEquals(7, assigneeIds.size());
        assertEquals(participants, Set.copyOf(assigneeIds.subList(0, 3)));
        assertEquals(participants, Set.copyOf(assigneeIds.subList(3, 6)));

        Map<Long, Integer> counts = new HashMap<>();
        for (Long assigneeId : assigneeIds) {
            counts.merge(assigneeId, 1, Integer::sum);
            int minimum = participants.stream()
                    .mapToInt(memberId -> counts.getOrDefault(memberId, 0))
                    .min()
                    .orElseThrow();
            int maximum = participants.stream()
                    .mapToInt(memberId -> counts.getOrDefault(memberId, 0))
                    .max()
                    .orElseThrow();
            assertTrue(maximum - minimum <= 1);
        }
        assertTrue(assignmentRepository.findAll().stream()
                .filter(attempt -> attempt.getOccurrence().getChore().getId().equals(chore.getId()))
                .allMatch(attempt ->
                        RotationAssignmentService.ALGORITHM_VERSION.equals(
                                attempt.getAlgorithmVersion()
                        )));
    }

    @Test
    void lowerValidAssignmentCountInSameFrequencyWinsForANewChore() {
        Context context = context("same-frequency-count");
        GroupMember second = addMember(context, "same-frequency-second");
        Instant historyStartedAt = Instant.parse("2026-08-03T03:00:00Z");
        Chore historyChore = choreRepository.save(Chore.daily(
                context.group(),
                context.ownerMembership(),
                "기존 일간 업무",
                ChoreEligibilityMode.ALL_ACTIVE_MEMBERS,
                LocalTime.of(19, 0),
                historyStartedAt.minusSeconds(60)
        ));
        List<ChoreOccurrence> history = generationService.ensureOccurrencesUntil(
                historyChore.getId(),
                historyStartedAt,
                LocalDate.of(2026, 8, 6)
        );

        Map<Long, Long> historyCounts = history.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        occurrence -> occurrence.currentAssignee().orElseThrow().getId(),
                        java.util.stream.Collectors.counting()
                ));
        Long lowerCountMemberId = List.of(context.ownerMembership(), second).stream()
                .map(GroupMember::getId)
                .min(java.util.Comparator.comparingLong(
                        memberId -> historyCounts.getOrDefault(memberId, 0L)
                ))
                .orElseThrow();
        assertEquals(1L, historyCounts.getOrDefault(lowerCountMemberId, 0L));

        Instant targetAt = Instant.parse("2026-08-06T03:00:00Z");
        Chore newChore = choreRepository.save(Chore.daily(
                context.group(),
                context.ownerMembership(),
                "새 일간 업무",
                ChoreEligibilityMode.ALL_ACTIVE_MEMBERS,
                LocalTime.of(21, 0),
                targetAt.minusSeconds(60)
        ));
        ChoreOccurrence assigned = generationService.ensureCurrentOccurrence(
                newChore.getId(),
                targetAt
        );

        assertEquals(lowerCountMemberId, assigned.currentAssignee().orElseThrow().getId());
        assertTrue(assignmentRepository
                .findFirstByOccurrence_IdAndEndedAtIsNull(assigned.getId())
                .orElseThrow()
                .getCandidateSnapshot()
                .contains("decision=HIGHER_VALID_SAME_FREQUENCY_ASSIGNMENT_COUNT"));
    }

    @Test
    void lowerCurrentPeriodLoadWinsAfterCycleAndFrequencyCountsTie() {
        Context context = context("current-period-load");
        addMember(context, "current-period-second");
        Instant reference = Instant.parse("2026-08-03T03:00:00Z");
        Chore firstChore = choreRepository.save(Chore.daily(
                context.group(), context.ownerMembership(), "첫 업무",
                ChoreEligibilityMode.ALL_ACTIVE_MEMBERS, LocalTime.of(19, 0),
                reference.minusSeconds(60)
        ));
        Chore secondChore = choreRepository.save(Chore.daily(
                context.group(), context.ownerMembership(), "두 번째 업무",
                ChoreEligibilityMode.ALL_ACTIVE_MEMBERS, LocalTime.of(20, 0),
                reference.minusSeconds(60)
        ));

        ChoreOccurrence first = generationService.ensureCurrentOccurrence(
                firstChore.getId(), reference
        );
        ChoreOccurrence second = generationService.ensureCurrentOccurrence(
                secondChore.getId(), reference
        );

        assertTrue(!first.currentAssignee().orElseThrow().getId().equals(
                second.currentAssignee().orElseThrow().getId()
        ));
    }

    @Test
    void weeklyChoresUseDifferentAssigneesInEachWeekWhenEnoughMembersExist() {
        Context context = context("weekly-period-distribution");
        GroupMember second = addMember(context, "weekly-period-second");
        GroupMember third = addMember(context, "weekly-period-third");
        Instant generatedAt = Instant.parse("2026-08-03T03:00:00Z");
        List<Chore> chores = choreRepository.saveAllAndFlush(List.of(
                Chore.weekly(
                        context.group(), context.ownerMembership(), "weekly-a",
                        ChoreEligibilityMode.ALL_ACTIVE_MEMBERS, DayOfWeek.SUNDAY,
                        LocalTime.of(19, 0), generatedAt.minusSeconds(60)
                ),
                Chore.weekly(
                        context.group(), context.ownerMembership(), "weekly-b",
                        ChoreEligibilityMode.ALL_ACTIVE_MEMBERS, DayOfWeek.SUNDAY,
                        LocalTime.of(20, 0), generatedAt.minusSeconds(60)
                ),
                Chore.weekly(
                        context.group(), context.ownerMembership(), "weekly-c",
                        ChoreEligibilityMode.ALL_ACTIVE_MEMBERS, DayOfWeek.SUNDAY,
                        LocalTime.of(21, 0), generatedAt.minusSeconds(60)
                )
        ));

        chores.forEach(chore -> planService.ensureRollingHorizon(chore, generatedAt));

        Set<Long> participants = Set.of(
                context.ownerMembership().getId(),
                second.getId(),
                third.getId()
        );
        Map<LocalDate, List<Long>> assigneesByWeek = occurrenceRepository.findAll().stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        ChoreOccurrence::getPeriodStart,
                        java.util.LinkedHashMap::new,
                        java.util.stream.Collectors.mapping(
                                occurrence -> occurrence.currentAssignee().orElseThrow().getId(),
                                java.util.stream.Collectors.toList()
                        )
                ));

        assertEquals(5, assigneesByWeek.size());
        assigneesByWeek.values().forEach(assignees -> {
            assertEquals(3, assignees.size());
            assertEquals(participants, Set.copyOf(assignees));
        });
        chores.forEach(chore -> {
            List<Long> firstCycle = occurrenceRepository.findAll().stream()
                    .filter(occurrence -> occurrence.getChore().getId().equals(chore.getId()))
                    .sorted(java.util.Comparator.comparing(ChoreOccurrence::getPeriodStart))
                    .limit(3)
                    .map(occurrence -> occurrence.currentAssignee().orElseThrow().getId())
                    .toList();
            assertEquals(participants, Set.copyOf(firstCycle));
        });
    }

    @Test
    void invalidatedAssignmentDoesNotConsumeAChoreCycleTurn() {
        Context context = context("invalid-assignment-cycle");
        addMember(context, "invalid-assignment-second");
        Instant reference = Instant.parse("2026-08-03T03:00:00Z");
        Chore chore = choreRepository.save(Chore.daily(
                context.group(), context.ownerMembership(), "수행 불가 테스트",
                ChoreEligibilityMode.ALL_ACTIVE_MEMBERS, LocalTime.of(20, 0),
                reference.minusSeconds(60)
        ));
        ChoreOccurrence first = generationService.ensureCurrentOccurrence(chore.getId(), reference);
        GroupMember invalidatedAssignee = first.currentAssignee().orElseThrow();

        commandService.declineCurrentOccurrence(
                first.getPublicId(),
                invalidatedAssignee.getPublicId(),
                reference.plusSeconds(60)
        );
        GroupMember validReplacement = first.currentAssignee().orElseThrow();
        Instant nextReference = reference.plusSeconds(86_400);
        LocalDate nextPeriodStart = nextReference
                .atZone(context.group().timeZone())
                .toLocalDate();

        assertEquals(0L, assignmentRepository
                .countValidAssignmentsForChoreAndMemberBeforePeriod(
                        chore.getId(),
                        nextPeriodStart,
                        invalidatedAssignee.getId()
                ));
        assertEquals(1L, assignmentRepository
                .countValidAssignmentsForChoreAndMemberBeforePeriod(
                        chore.getId(),
                        nextPeriodStart,
                        validReplacement.getId()
                ));
        ChoreOccurrence next = generationService.ensureCurrentOccurrence(
                chore.getId(),
                nextReference
        );

        assertTrue(!invalidatedAssignee.getId().equals(validReplacement.getId()));
        assertEquals(invalidatedAssignee.getId(), next.currentAssignee().orElseThrow().getId());
    }

    @Test
    void newMemberFairnessCreditIgnoresFuturePlannedAssignments() {
        Context context = context("future-plan-credit");
        addMember(context, "future-plan-existing");
        Instant reference = Instant.parse("2026-08-03T03:00:00Z");
        Chore chore = choreRepository.save(Chore.daily(
                context.group(), context.ownerMembership(), "미래 계획 크레딧",
                ChoreEligibilityMode.ALL_ACTIVE_MEMBERS, LocalTime.of(20, 0),
                reference.minusSeconds(60)
        ));
        List<ChoreOccurrence> planned = generationService.ensureOccurrencesUntil(
                chore.getId(),
                reference,
                LocalDate.of(2026, 8, 10)
        );
        assertEquals(7, planned.size());

        GroupMember joining = addMember(context, "future-plan-joining");
        Instant joinedAt = reference.plusSeconds(60);
        assertTrue(enrollmentService.addOrReactivate(chore, joining, joinedAt));

        ChoreEligibleMember enrollment = choreEligibleMemberRepository
                .findByChore_IdAndMember_Id(chore.getId(), joining.getId())
                .orElseThrow();
        assertEquals(2L, enrollment.getFairnessCredit());
    }

    @Test
    void persistsCurrentWeekAndFourFutureWeeksAndRegeneratesFuturePlan() {
        Context context = context("rolling-horizon");
        Instant generatedAt = Instant.parse("2026-08-03T03:00:00Z");
        LocalDate currentWeekStart = LocalDate.of(2026, 8, 3);
        Chore daily = choreRepository.save(Chore.daily(
                context.group(), context.ownerMembership(), "매일 청소",
                ChoreEligibilityMode.ALL_ACTIVE_MEMBERS, LocalTime.of(20, 0), generatedAt
        ));
        Chore weekly = choreRepository.save(Chore.weekly(
                context.group(), context.ownerMembership(), "주간 청소",
                ChoreEligibilityMode.ALL_ACTIVE_MEMBERS, DayOfWeek.SUNDAY,
                LocalTime.of(20, 0), generatedAt
        ));
        Chore biweekly = choreRepository.save(Chore.biweekly(
                context.group(), context.ownerMembership(), "격주 청소",
                ChoreEligibilityMode.ALL_ACTIVE_MEMBERS, currentWeekStart,
                LocalTime.of(20, 0), generatedAt
        ));

        planService.ensureRollingHorizon(daily, generatedAt);
        planService.ensureRollingHorizon(weekly, generatedAt);
        planService.ensureRollingHorizon(biweekly, generatedAt);

        List<ChoreOccurrence> initial = occurrenceRepository.findAll();
        assertEquals(35, countFor(initial, daily));
        assertEquals(5, countFor(initial, weekly));
        assertEquals(3, countFor(initial, biweekly));
        assertEquals(28, countFutureFor(initial, daily, currentWeekStart.plusWeeks(1)));
        assertEquals(4, countFutureFor(initial, weekly, currentWeekStart.plusWeeks(1)));
        assertEquals(2, countFutureFor(initial, biweekly, currentWeekStart.plusWeeks(1)));
        assertTrue(initial.stream().allMatch(item -> item.getCreatedAt().equals(generatedAt)));
        assertTrue(assignmentRepository.findAll().stream()
                .allMatch(item -> item.getAssignedAt().equals(generatedAt)));

        long initialAssignmentCount = assignmentRepository.count();
        planService.ensureRollingHorizon(daily, generatedAt.plusSeconds(60));
        assertEquals(initial.size(), occurrenceRepository.count());
        assertEquals(initialAssignmentCount, assignmentRepository.count());

        Instant regeneratedAt = generatedAt.plusSeconds(120);
        planService.regenerateFuture(daily, regeneratedAt);

        List<ChoreOccurrence> all = occurrenceRepository.findAll();
        List<ChoreOccurrence> cancelled = all.stream()
                .filter(item -> item.getChore().getId().equals(daily.getId()))
                .filter(item -> item.getStatus() == OccurrenceStatus.CANCELLED)
                .toList();
        assertEquals(34, cancelled.size());
        assertEquals(35, all.stream()
                .filter(item -> item.getChore().getId().equals(daily.getId()))
                .filter(item -> item.getStatus() != OccurrenceStatus.CANCELLED)
                .count());
        assertTrue(cancelled.stream().allMatch(item -> assignmentRepository
                .findAllByOccurrence_IdOrderBySequenceNumber(item.getId())
                .getLast()
                .getEndReason() == AssignmentEndReason.PLAN_REGENERATED));

        daily.deactivate();
        planService.cancelFutureForDeactivation(daily, regeneratedAt.plusSeconds(60));
        assertTrue(occurrenceRepository.findAll().stream()
                .filter(item -> item.getChore().getId().equals(daily.getId()))
                .allMatch(item -> item.getStatus() == OccurrenceStatus.CANCELLED));
    }

    @Test
    void createsAndAssignsCurrentOccurrenceIdempotently() {
        Context context = context("all-active");
        Chore chore = choreRepository.save(Chore.daily(
                context.group(),
                context.ownerMembership(),
                "설거지",
                ChoreEligibilityMode.ALL_ACTIVE_MEMBERS,
                LocalTime.of(22, 0),
                Instant.parse("2026-07-23T00:00:00Z")
        ));
        Instant reference = Instant.parse("2026-07-23T03:00:00Z");

        ChoreOccurrence first = generationService.ensureCurrentOccurrence(chore.getId(), reference);
        ChoreOccurrence second = generationService.ensureCurrentOccurrence(chore.getId(), reference);

        assertSame(first, second);
        assertEquals(1, occurrenceRepository.count());
        assertEquals(1, assignmentRepository.count());
        assertEquals(1, decisionLogRepository.count());
        assertEquals(OccurrenceStatus.ASSIGNED, first.getStatus());
        assertEquals(
                context.ownerMembership().getId(),
                first.currentAssignee().orElseThrow().getId()
        );
    }

    @ParameterizedTest
    @EnumSource(ChoreFrequency.class)
    void createsOccurrenceWhenDatabaseRoundsHighPrecisionCreationTime(
            ChoreFrequency frequency
    ) {
        Context context = context("high-precision-" + frequency);
        Instant reference = Instant.parse("2026-07-23T03:00:00.123456789Z");
        Chore chore = switch (frequency) {
            case DAILY -> Chore.daily(
                    context.group(),
                    context.ownerMembership(),
                    "일간 업무",
                    ChoreEligibilityMode.ALL_ACTIVE_MEMBERS,
                    LocalTime.of(20, 0),
                    reference
            );
            case WEEKLY -> Chore.weekly(
                    context.group(),
                    context.ownerMembership(),
                    "주간 업무",
                    ChoreEligibilityMode.ALL_ACTIVE_MEMBERS,
                    DayOfWeek.SUNDAY,
                    LocalTime.of(20, 0),
                    reference
            );
            case BIWEEKLY -> Chore.biweekly(
                    context.group(),
                    context.ownerMembership(),
                    "격주 업무",
                    ChoreEligibilityMode.ALL_ACTIVE_MEMBERS,
                    LocalDate.of(2026, 7, 20),
                    LocalTime.of(20, 0),
                    reference
            );
        };
        choreRepository.save(chore);

        ChoreOccurrence occurrence =
                generationService.ensureCurrentOccurrence(chore.getId(), reference);

        assertEquals(OccurrenceStatus.ASSIGNED, occurrence.getStatus());
        assertEquals(1, assignmentRepository.countByOccurrence_Id(occurrence.getId()));
        assertEquals(1, decisionLogRepository.countByOccurrence_Id(occurrence.getId()));
    }

    @Test
    void selectedMemberRestrictionExcludesOtherActiveMembers() {
        Context context = context("selected");
        User selectedUser = userRepository.save(user("selected-member"));
        GroupMember selected = groupMemberRepository.save(
                GroupMember.member(context.group(), selectedUser)
        );
        Chore chore = choreRepository.save(Chore.daily(
                context.group(),
                context.ownerMembership(),
                "가스 점검",
                ChoreEligibilityMode.SELECTED_MEMBERS,
                LocalTime.of(10, 0),
                Instant.parse("2026-07-23T00:00:00Z")
        ));
        choreEligibleMemberRepository.save(new ChoreEligibleMember(chore, selected));

        Instant reference = Instant.parse("2026-07-23T03:00:00Z");
        ChoreOccurrence occurrence =
                generationService.ensureCurrentOccurrence(chore.getId(), reference);

        assertEquals(OccurrenceStatus.ASSIGNED, occurrence.getStatus());
        assertEquals(selected.getId(), occurrence.currentAssignee().orElseThrow().getId());
        assertTrue(
                assignmentRepository
                        .findFirstByOccurrence_IdAndEndedAtIsNull(occurrence.getId())
                        .orElseThrow()
                        .getCandidateSnapshot()
                        .contains("decision=NOT_ELIGIBLE")
        );
    }

    @Test
    void missingSelectedCandidatesCreatesNeedsAttentionOccurrence() {
        Context context = context("no-selected");
        Chore chore = choreRepository.save(Chore.daily(
                context.group(),
                context.ownerMembership(),
                "보일러 점검",
                ChoreEligibilityMode.SELECTED_MEMBERS,
                LocalTime.of(10, 0),
                Instant.parse("2026-07-23T00:00:00Z")
        ));

        Instant reference = Instant.parse("2026-07-23T03:00:00Z");
        ChoreOccurrence occurrence =
                generationService.ensureCurrentOccurrence(chore.getId(), reference);

        assertEquals(OccurrenceStatus.NEEDS_ATTENTION, occurrence.getStatus());
        assertTrue(occurrence.currentAssignee().isEmpty());
        assertEquals(0, assignmentRepository.count());
        assertEquals(1, decisionLogRepository.count());
        assertEquals(
                "NO_CANDIDATE",
                decisionLogRepository.findAll().getFirst().getOutcome().name()
        );
        assertEquals(
                NoCandidateReason.NO_ACTIVE_ELIGIBLE_NON_DECLINED_CANDIDATE,
                occurrence.getAttentionReason()
        );
        assertEquals(reference, occurrence.getAttentionSince());
        assertEquals(reference, occurrence.getLastDecisionAt());
    }

    @Test
    void scheduleChangeMovesActiveOccurrenceAndKeepsAssignmentAndStatus() {
        Context context = context("schedule-change");
        Chore chore = choreRepository.save(Chore.daily(
                context.group(),
                context.ownerMembership(),
                "공용 청소",
                ChoreEligibilityMode.ALL_ACTIVE_MEMBERS,
                LocalTime.of(20, 0),
                Instant.parse("2026-07-23T00:00:00Z")
        ));
        ChoreOccurrence daily = generationService.ensureCurrentOccurrence(
                chore.getId(),
                Instant.parse("2026-07-23T03:00:00Z")
        );
        Long assigneeId = daily.currentAssignee().orElseThrow().getId();

        chore.reschedule(
                ChoreFrequency.WEEKLY,
                LocalTime.of(19, 0),
                DayOfWeek.SUNDAY,
                null
        );
        choreRepository.saveAndFlush(chore);

        ChoreOccurrence rescheduled = generationService.rescheduleActiveOccurrence(
                chore,
                Instant.parse("2026-07-23T04:00:00Z")
        ).orElseThrow();
        ChoreOccurrence ensured = generationService.ensureCurrentOccurrence(
                chore.getId(),
                Instant.parse("2026-07-23T04:00:00Z")
        );

        assertSame(daily, rescheduled);
        assertSame(daily, ensured);
        assertEquals(1, occurrenceRepository.count());
        assertEquals(1L, chore.getScheduleRevision());
        assertEquals(1L, daily.getScheduleRevisionSnapshot());
        assertEquals(ChoreFrequency.WEEKLY, daily.getFrequencySnapshot());
        assertEquals(LocalDate.of(2026, 7, 20), daily.getPeriodStart());
        assertEquals(LocalDate.of(2026, 7, 27), daily.getPeriodEndExclusive());
        assertEquals(Instant.parse("2026-07-26T10:00:00Z"), daily.getDueAt());
        assertEquals(OccurrenceStatus.ASSIGNED, daily.getStatus());
        assertEquals(assigneeId, daily.currentAssignee().orElseThrow().getId());
    }

    @Test
    void completedOccurrenceKeepsItsSnapshotAndNextGenerationUsesNewRevision() {
        Context context = context("completed-schedule-change");
        Chore chore = choreRepository.save(Chore.daily(
                context.group(),
                context.ownerMembership(),
                "공용 청소",
                ChoreEligibilityMode.ALL_ACTIVE_MEMBERS,
                LocalTime.of(20, 0),
                Instant.parse("2026-07-23T00:00:00Z")
        ));
        ChoreOccurrence completed = generationService.ensureCurrentOccurrence(
                chore.getId(),
                Instant.parse("2026-07-23T03:00:00Z")
        );
        completed.complete(Instant.parse("2026-07-23T04:00:00Z"));
        occurrenceRepository.saveAndFlush(completed);

        chore.reschedule(ChoreFrequency.DAILY, LocalTime.of(19, 0), null, null);
        choreRepository.saveAndFlush(chore);

        assertTrue(generationService.rescheduleActiveOccurrence(
                chore,
                Instant.parse("2026-07-23T05:00:00Z")
        ).isEmpty());
        assertEquals(1, occurrenceRepository.count());
        assertEquals(0L, completed.getScheduleRevisionSnapshot());
        assertEquals(Instant.parse("2026-07-23T11:00:00Z"), completed.getDueAt());

        ChoreOccurrence overlapping = generationService.ensureCurrentOccurrence(
                chore.getId(),
                Instant.parse("2026-07-23T05:00:00Z")
        );

        assertSame(completed, overlapping);
        assertEquals(1, occurrenceRepository.count());

        ChoreOccurrence next = generationService.ensureCurrentOccurrence(
                chore.getId(),
                Instant.parse("2026-07-24T05:00:00Z")
        );

        assertEquals(2, occurrenceRepository.count());
        assertEquals(LocalDate.of(2026, 7, 24), next.getPeriodStart());
        assertEquals(1L, next.getScheduleRevisionSnapshot());
        assertEquals(Instant.parse("2026-07-24T10:00:00Z"), next.getDueAt());
    }

    private Context context(String suffix) {
        User owner = userRepository.save(user("owner-" + suffix));
        SharingGroup group = groupRepository.save(new SharingGroup("우리 집", owner));
        GroupMember membership = groupMemberRepository.save(GroupMember.owner(group, owner));
        return new Context(group, membership);
    }

    private GroupMember addMember(Context context, String suffix) {
        User member = userRepository.save(user(suffix));
        return groupMemberRepository.save(GroupMember.member(context.group(), member));
    }

    private long countFor(List<ChoreOccurrence> occurrences, Chore chore) {
        return occurrences.stream()
                .filter(item -> item.getChore().getId().equals(chore.getId()))
                .count();
    }

    private long countFutureFor(
            List<ChoreOccurrence> occurrences,
            Chore chore,
            LocalDate firstFutureWeek
    ) {
        return occurrences.stream()
                .filter(item -> item.getChore().getId().equals(chore.getId()))
                .filter(item -> !item.getPeriodStart().isBefore(firstFutureWeek))
                .count();
    }

    private User user(String providerUserId) {
        return User.builder()
                .provider(OAuthProvider.GOOGLE)
                .providerUserId(providerUserId + "-" + System.nanoTime())
                .build();
    }

    private record Context(SharingGroup group, GroupMember ownerMembership) {
    }
}
