package gdg.sharinglog.service.rotation.api.chore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import gdg.sharinglog.domain.GroupMember;
import gdg.sharinglog.domain.OAuthProvider;
import gdg.sharinglog.domain.SharingGroup;
import gdg.sharinglog.domain.User;
import gdg.sharinglog.domain.rotation.Chore;
import gdg.sharinglog.domain.rotation.ChoreEligibilityMode;
import gdg.sharinglog.domain.rotation.ChoreFrequency;
import gdg.sharinglog.domain.rotation.ChoreOccurrence;
import gdg.sharinglog.repository.GroupMemberRepository;
import gdg.sharinglog.repository.rotation.ChoreRepository;
import gdg.sharinglog.service.rotation.enrollment.ChoreEnrollmentService;
import gdg.sharinglog.service.rotation.occurrence.OccurrenceGenerationService;
import gdg.sharinglog.service.rotation.occurrence.OccurrencePlanService;
import gdg.sharinglog.service.rotation.access.RotationActor;
import gdg.sharinglog.service.rotation.access.RotationActorAccessService;
import gdg.sharinglog.web.rotation.error.RotationBadRequestException;
import gdg.sharinglog.web.rotation.error.RotationFieldError;
import gdg.sharinglog.web.rotation.error.RotationProblemCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ChoreApplicationServiceTest {

    private static final String REGISTRATION_ID = "google";
    private static final Instant CHANGED_AT = Instant.parse("2026-07-23T04:00:00Z");

    @Mock
    RotationActorAccessService accessService;

    @Mock
    GroupMemberRepository groupMemberRepository;

    @Mock
    ChoreRepository choreRepository;

    @Mock
    ChoreEnrollmentService enrollmentService;

    @Mock
    OccurrenceGenerationService occurrenceGenerationService;

    @Mock
    OccurrencePlanService occurrencePlanService;

    @Mock
    OAuth2User principal;

    @InjectMocks
    ChoreApplicationService service;

    private SharingGroup group;
    private GroupMember ownerMembership;
    private Chore chore;

    @BeforeEach
    void setUp() {
        User owner = User.builder()
                .provider(OAuthProvider.GOOGLE)
                .providerUserId("schedule-update-owner")
                .build();
        group = new SharingGroup("우리 집", owner);
        ReflectionTestUtils.setField(group, "id", 10L);
        ownerMembership = GroupMember.owner(group, owner);
        chore = Chore.daily(
                group,
                ownerMembership,
                "공용 청소",
                ChoreEligibilityMode.ALL_ACTIVE_MEMBERS,
                LocalTime.of(20, 0),
                Instant.parse("2026-07-23T00:00:00Z")
        );
    }

    @Test
    void createRejectsBiweeklyDueDateOnTheRequestDateWithFieldError() {
        RotationActor actor = new RotationActor(group, ownerMembership, ownerMembership.getUser());
        when(accessService.requireOwnerForUpdate(group.getPublicId(), REGISTRATION_ID, principal))
                .thenReturn(actor);
        CreateChoreCommand command = new CreateChoreCommand(
                "격주 청소",
                ChoreFrequency.BIWEEKLY,
                LocalTime.of(20, 0),
                null,
                LocalDate.of(2026, 7, 23),
                ChoreEligibilityMode.ALL_ACTIVE_MEMBERS,
                List.of()
        );

        RotationBadRequestException exception = assertThrows(
                RotationBadRequestException.class,
                () -> service.create(
                        group.getPublicId(),
                        REGISTRATION_ID,
                        principal,
                        command,
                        CHANGED_AT
                )
        );

        String reason = "격주 마감일은 요청일 다음 날 이후여야 합니다.";
        assertEquals(RotationProblemCode.VALIDATION_FAILED, exception.problem());
        assertEquals(reason, exception.getMessage());
        assertEquals(
                List.of(new RotationFieldError("schedule.biweeklyDueDate", reason)),
                exception.properties().get("errors")
        );
        verify(choreRepository, never()).saveAndFlush(any());
        verify(enrollmentService, never()).initializeChore(any(), any(), any());
        verify(occurrencePlanService, never()).ensureRollingHorizon(any(), any());
    }

    @Test
    void createRejectsBiweeklyDueDateOutsideTheSupportedRangeWithFieldError() {
        RotationActor actor = new RotationActor(group, ownerMembership, ownerMembership.getUser());
        when(accessService.requireOwnerForUpdate(group.getPublicId(), REGISTRATION_ID, principal))
                .thenReturn(actor);
        CreateChoreCommand command = new CreateChoreCommand(
                "격주 청소",
                ChoreFrequency.BIWEEKLY,
                LocalTime.of(20, 0),
                null,
                Chore.MAX_BIWEEKLY_DUE_DATE.plusDays(1),
                ChoreEligibilityMode.ALL_ACTIVE_MEMBERS,
                List.of()
        );

        RotationBadRequestException exception = assertThrows(
                RotationBadRequestException.class,
                () -> service.create(
                        group.getPublicId(),
                        REGISTRATION_ID,
                        principal,
                        command,
                        CHANGED_AT
                )
        );

        String reason = "격주 마감일이 지원 날짜 범위를 벗어났습니다.";
        assertEquals(RotationProblemCode.VALIDATION_FAILED, exception.problem());
        assertEquals(reason, exception.getMessage());
        assertEquals(
                List.of(new RotationFieldError("schedule.biweeklyDueDate", reason)),
                exception.properties().get("errors")
        );
        verify(choreRepository, never()).saveAndFlush(any());
        verify(occurrencePlanService, never()).ensureRollingHorizon(any(), any());
    }

    @Test
    void createAcceptsTomorrowAsBiweeklyDueDateOnAnyWeekday() {
        RotationActor actor = new RotationActor(group, ownerMembership, ownerMembership.getUser());
        when(accessService.requireOwnerForUpdate(group.getPublicId(), REGISTRATION_ID, principal))
                .thenReturn(actor);
        when(choreRepository.saveAndFlush(any(Chore.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(occurrencePlanService.ensureRollingHorizon(any(Chore.class), eq(CHANGED_AT)))
                .thenReturn(List.of(mock(ChoreOccurrence.class)));
        LocalDate firstDueDate = LocalDate.of(2026, 7, 24);

        CreatedChore created = service.create(
                group.getPublicId(),
                REGISTRATION_ID,
                principal,
                new CreateChoreCommand(
                        "격주 청소",
                        ChoreFrequency.BIWEEKLY,
                        LocalTime.of(20, 0),
                        null,
                        firstDueDate,
                        ChoreEligibilityMode.ALL_ACTIVE_MEMBERS,
                        List.of()
                ),
                CHANGED_AT
        );

        assertEquals(firstDueDate, created.chore().chore().getBiweeklyDueDate());
    }

    @Test
    void updateRejectsChangingBiweeklyDueDateToTheRequestDate() {
        RotationActor actor = new RotationActor(group, ownerMembership, ownerMembership.getUser());
        when(accessService.requireOwnerForUpdate(group.getPublicId(), REGISTRATION_ID, principal))
                .thenReturn(actor);
        when(choreRepository.findByPublicIdAndGroupPublicIdForUpdate(
                chore.getPublicId(),
                group.getPublicId()
        )).thenReturn(Optional.of(chore));

        RotationBadRequestException exception = assertThrows(
                RotationBadRequestException.class,
                () -> service.update(
                        group.getPublicId(),
                        chore.getPublicId(),
                        REGISTRATION_ID,
                        principal,
                        new UpdateChoreCommand(
                                null,
                                new UpdateChoreCommand.Schedule(
                                        ChoreFrequency.BIWEEKLY,
                                        LocalTime.of(20, 0),
                                        null,
                                        LocalDate.of(2026, 7, 23)
                                ),
                                null
                        ),
                        0L,
                        CHANGED_AT
                )
        );

        assertEquals(RotationProblemCode.VALIDATION_FAILED, exception.problem());
        verify(choreRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateAllowsKeepingHistoricalBiweeklyDueDateWhenChangingOnlyTheTime() {
        LocalDate historicalDueDate = LocalDate.of(2026, 7, 1);
        chore = Chore.biweekly(
                group,
                ownerMembership,
                "공용 청소",
                ChoreEligibilityMode.ALL_ACTIVE_MEMBERS,
                historicalDueDate,
                LocalTime.of(20, 0),
                Instant.parse("2026-06-01T00:00:00Z")
        );
        RotationActor actor = new RotationActor(group, ownerMembership, ownerMembership.getUser());
        when(accessService.requireOwnerForUpdate(group.getPublicId(), REGISTRATION_ID, principal))
                .thenReturn(actor);
        when(choreRepository.findByPublicIdAndGroupPublicIdForUpdate(
                chore.getPublicId(),
                group.getPublicId()
        )).thenReturn(Optional.of(chore));
        when(choreRepository.saveAndFlush(chore)).thenReturn(chore);
        when(enrollmentService.findActiveMembers(chore)).thenReturn(List.of(ownerMembership));

        ChoreView updated = service.update(
                group.getPublicId(),
                chore.getPublicId(),
                REGISTRATION_ID,
                principal,
                new UpdateChoreCommand(
                        null,
                        new UpdateChoreCommand.Schedule(
                                ChoreFrequency.BIWEEKLY,
                                LocalTime.of(19, 0),
                                null,
                                historicalDueDate
                        ),
                        null
                ),
                0L,
                CHANGED_AT
        );

        assertEquals(historicalDueDate, updated.chore().getBiweeklyDueDate());
        assertEquals(LocalTime.of(19, 0), updated.chore().getDueTime());
        verify(occurrenceGenerationService).rescheduleActiveOccurrence(chore, CHANGED_AT);
        verify(occurrencePlanService).regenerateFutureAfterScheduleChange(chore, CHANGED_AT);
    }

    @Test
    void updatePassesReferenceTimeAndNewRevisionToActiveOccurrence() {
        RotationActor actor = new RotationActor(group, ownerMembership, ownerMembership.getUser());
        when(accessService.requireOwnerForUpdate(group.getPublicId(), REGISTRATION_ID, principal))
                .thenReturn(actor);
        when(choreRepository.findByPublicIdAndGroupPublicIdForUpdate(
                chore.getPublicId(),
                group.getPublicId()
        )).thenReturn(Optional.of(chore));
        when(choreRepository.saveAndFlush(chore)).thenReturn(chore);
        when(enrollmentService.findActiveMembers(chore)).thenReturn(List.of(ownerMembership));

        ChoreView updated = service.update(
                group.getPublicId(),
                chore.getPublicId(),
                REGISTRATION_ID,
                principal,
                new UpdateChoreCommand(
                        null,
                        new UpdateChoreCommand.Schedule(
                                ChoreFrequency.WEEKLY,
                                LocalTime.of(19, 0),
                                DayOfWeek.SUNDAY,
                                null
                        ),
                        null
                ),
                0L,
                CHANGED_AT
        );

        assertEquals(1L, updated.chore().getScheduleRevision());
        verify(occurrenceGenerationService).rescheduleActiveOccurrence(chore, CHANGED_AT);
        verify(occurrencePlanService).regenerateFutureAfterScheduleChange(chore, CHANGED_AT);
    }

    @Test
    void updateReplacesEligibleMembersAndRegeneratesFutureOccurrences() {
        GroupMember firstSelected = member("first-selected", 21L);
        GroupMember secondSelected = member("second-selected", 22L);
        GroupMember removed = member("removed", 23L);
        RotationActor actor = new RotationActor(group, ownerMembership, ownerMembership.getUser());
        when(accessService.requireOwnerForUpdate(group.getPublicId(), REGISTRATION_ID, principal))
                .thenReturn(actor);
        when(choreRepository.findByPublicIdAndGroupPublicIdForUpdate(
                chore.getPublicId(),
                group.getPublicId()
        )).thenReturn(Optional.of(chore));
        when(groupMemberRepository.findAllByGroup_IdAndPublicIdInAndStatusOrderById(
                group.getId(),
                java.util.Set.of(firstSelected.getPublicId(), secondSelected.getPublicId()),
                gdg.sharinglog.domain.MemberStatus.ACTIVE
        )).thenReturn(List.of(firstSelected, secondSelected));
        when(enrollmentService.findActiveMembers(chore))
                .thenReturn(
                        List.of(firstSelected, secondSelected, removed),
                        List.of(firstSelected, secondSelected)
                );
        when(enrollmentService.removeOrDisable(chore, removed, CHANGED_AT)).thenReturn(true);
        when(choreRepository.saveAndFlush(chore)).thenReturn(chore);

        ChoreView updated = service.update(
                group.getPublicId(),
                chore.getPublicId(),
                REGISTRATION_ID,
                principal,
                new UpdateChoreCommand(
                        null,
                        null,
                        new UpdateChoreCommand.Eligibility(
                                ChoreEligibilityMode.SELECTED_MEMBERS,
                                List.of(
                                        firstSelected.getPublicId(),
                                        secondSelected.getPublicId()
                                )
                        )
                ),
                0L,
                CHANGED_AT
        );

        assertEquals(ChoreEligibilityMode.SELECTED_MEMBERS, updated.chore().getEligibilityMode());
        assertEquals(List.of(firstSelected, secondSelected), updated.eligibleMembers());
        verify(enrollmentService).removeOrDisable(chore, removed, CHANGED_AT);
        verify(enrollmentService, never()).removeOrDisable(chore, firstSelected, CHANGED_AT);
        verify(enrollmentService, never()).removeOrDisable(chore, secondSelected, CHANGED_AT);
        verify(occurrencePlanService).regenerateFuture(chore, CHANGED_AT);
    }

    @Test
    void deactivateCancelsFuturePlanWithoutCreatingReplacement() {
        RotationActor actor = new RotationActor(group, ownerMembership, ownerMembership.getUser());
        when(accessService.requireOwnerForUpdate(group.getPublicId(), REGISTRATION_ID, principal))
                .thenReturn(actor);
        when(choreRepository.findByPublicIdAndGroupPublicIdForUpdate(
                chore.getPublicId(),
                group.getPublicId()
        )).thenReturn(Optional.of(chore));
        when(choreRepository.saveAndFlush(chore)).thenReturn(chore);

        service.deactivate(
                group.getPublicId(),
                chore.getPublicId(),
                REGISTRATION_ID,
                principal,
                0L,
                CHANGED_AT
        );

        assertFalse(chore.isActive());
        verify(occurrencePlanService).cancelFutureForDeactivation(chore, CHANGED_AT);
    }

    private GroupMember member(String providerUserId, long id) {
        User user = User.builder()
                .provider(OAuthProvider.GOOGLE)
                .providerUserId(providerUserId)
                .build();
        GroupMember member = GroupMember.member(group, user);
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }
}
