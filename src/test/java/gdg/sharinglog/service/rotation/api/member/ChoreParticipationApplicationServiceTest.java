package gdg.sharinglog.service.rotation.api.member;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import gdg.sharinglog.domain.GroupMember;
import gdg.sharinglog.domain.SharingGroup;
import gdg.sharinglog.domain.rotation.AssignmentEndReason;
import gdg.sharinglog.domain.rotation.AssignmentTrigger;
import gdg.sharinglog.domain.rotation.Chore;
import gdg.sharinglog.domain.rotation.ChoreOccurrence;
import gdg.sharinglog.domain.rotation.OccurrenceStatus;
import gdg.sharinglog.repository.rotation.ChoreOccurrenceRepository;
import gdg.sharinglog.repository.rotation.ChoreRepository;
import gdg.sharinglog.repository.rotation.OccurrenceEligibleMemberRepository;
import gdg.sharinglog.service.rotation.assignment.RotationAssignmentService;
import gdg.sharinglog.service.rotation.enrollment.ChoreEnrollmentService;
import gdg.sharinglog.service.rotation.occurrence.OccurrencePlanService;
import gdg.sharinglog.service.rotation.access.RotationActor;
import gdg.sharinglog.service.rotation.access.RotationActorAccessService;
import gdg.sharinglog.service.rotation.substitute.SubstituteRequestLifecycleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.core.user.OAuth2User;

@ExtendWith(MockitoExtension.class)
class ChoreParticipationApplicationServiceTest {

    private static final String GROUP_PUBLIC_ID =
            "11111111-1111-4111-8111-111111111111";
    private static final String MEMBER_PUBLIC_ID =
            "22222222-2222-4222-8222-222222222222";
    private static final String CHORE_PUBLIC_ID =
            "33333333-3333-4333-8333-333333333333";
    private static final String REGISTRATION_ID = "google";
    private static final Instant CHANGED_AT = Instant.parse("2026-07-28T10:00:00Z");
    private static final long GROUP_ID = 10L;
    private static final long MEMBER_ID = 20L;
    private static final long OTHER_MEMBER_ID = 21L;
    private static final long CHORE_ID = 30L;
    private static final long CHORE_VERSION = 4L;

    @Mock
    RotationActorAccessService accessService;

    @Mock
    ChoreRepository choreRepository;

    @Mock
    ChoreOccurrenceRepository occurrenceRepository;

    @Mock
    OccurrenceEligibleMemberRepository eligibilityRepository;

    @Mock
    ChoreEnrollmentService enrollmentService;

    @Mock
    RotationAssignmentService assignmentService;

    @Mock
    SubstituteRequestLifecycleService substituteRequestLifecycleService;

    @Mock
    OccurrencePlanService occurrencePlanService;

    @Mock
    OAuth2User principal;

    @Mock
    RotationActor actor;

    @Mock
    SharingGroup group;

    @Mock
    GroupMember target;

    @Mock
    GroupMember otherMember;

    @Mock
    Chore chore;

    @Mock
    ChoreOccurrence occurrence;

    @InjectMocks
    ChoreParticipationApplicationService service;

    @BeforeEach
    void setUpLockedResources() {
        when(accessService.requireOwnerForUpdate(GROUP_PUBLIC_ID, REGISTRATION_ID, principal))
                .thenReturn(actor);
        when(actor.group()).thenReturn(group);
        when(group.getId()).thenReturn(GROUP_ID);
        when(accessService.requireActiveTargetMemberForUpdate(actor, MEMBER_PUBLIC_ID))
                .thenReturn(target);
        when(target.getPublicId()).thenReturn(MEMBER_PUBLIC_ID);

        when(chore.getId()).thenReturn(CHORE_ID);
        when(chore.getPublicId()).thenReturn(CHORE_PUBLIC_ID);
        when(chore.getVersion()).thenReturn(CHORE_VERSION);
        when(choreRepository.findAllByGroup_IdOrderById(GROUP_ID)).thenReturn(List.of(chore));
        when(choreRepository.findByIdForUpdate(CHORE_ID)).thenReturn(Optional.of(chore));
    }

    @Test
    void nextOccurrenceChangesEnrollmentWithoutTouchingCurrentOccurrences() {
        when(enrollmentService.addOrReactivate(chore, target, CHANGED_AT)).thenReturn(true);

        UpdatedChoreParticipations updated = service.update(
                GROUP_PUBLIC_ID,
                MEMBER_PUBLIC_ID,
                REGISTRATION_ID,
                principal,
                command(UpdatedChoreParticipations.Action.ADD,
                        ChoreParticipationApplicationScope.NEXT_OCCURRENCE),
                CHANGED_AT
        );

        assertTrue(updated.chores().getFirst().changed());
        assertEquals(0, updated.chores().getFirst().rebuiltOccurrenceCount());
        verify(enrollmentService).addOrReactivate(chore, target, CHANGED_AT);
        verifyNoInteractions(occurrenceRepository, eligibilityRepository, assignmentService);
        verify(occurrencePlanService).regenerateFutures(List.of(chore), CHANGED_AT);
    }

    @Test
    void currentAndFutureRemovalRebuildsSnapshotAndReassignsRemovedAssignee() {
        when(enrollmentService.removeOrDisable(chore, target, CHANGED_AT)).thenReturn(true);
        when(enrollmentService.findActiveMembers(chore)).thenReturn(List.of(otherMember));
        when(target.getId()).thenReturn(MEMBER_ID);
        when(chore.getGroup()).thenReturn(group);
        when(group.timeZone()).thenReturn(java.time.ZoneId.of("Asia/Seoul"));
        when(otherMember.getId()).thenReturn(OTHER_MEMBER_ID);
        when(otherMember.isActive()).thenReturn(true);
        when(otherMember.getGroup()).thenReturn(group);
        when(otherMember.getActivationGeneration()).thenReturn(1L);

        when(occurrence.getId()).thenReturn(40L);
        when(occurrence.getChore()).thenReturn(chore);
        when(occurrence.currentAssignee()).thenReturn(Optional.of(target));
        when(occurrence.getEligibilitySnapshotVersion()).thenReturn(2);
        when(occurrence.getStatus()).thenReturn(OccurrenceStatus.ASSIGNED);
        when(occurrenceRepository.findAllOpenActiveOnByChoreIdForUpdate(
                CHORE_ID,
                java.time.LocalDate.of(2026, 7, 28)
        ))
                .thenReturn(List.of(occurrence));

        UpdatedChoreParticipations updated = service.update(
                GROUP_PUBLIC_ID,
                MEMBER_PUBLIC_ID,
                REGISTRATION_ID,
                principal,
                command(UpdatedChoreParticipations.Action.REMOVE,
                        ChoreParticipationApplicationScope.CURRENT_AND_FUTURE),
                CHANGED_AT
        );

        var result = updated.chores().getFirst();
        assertEquals(1, result.rebuiltOccurrenceCount());
        assertEquals(1, result.reassignedOccurrenceCount());
        verify(occurrence).releaseForReassignment(
                AssignmentEndReason.PARTICIPATION_REMOVED,
                CHANGED_AT
        );
        verify(occurrence).advanceEligibilitySnapshotVersion();
        verify(eligibilityRepository).saveAllAndFlush(anyList());
        verify(assignmentService).assign(
                40L,
                AssignmentTrigger.PARTICIPATION_CHANGE_REASSIGNMENT,
                CHANGED_AT
        );
    }

    private UpdateChoreParticipationsCommand command(
            UpdatedChoreParticipations.Action action,
            ChoreParticipationApplicationScope scope
    ) {
        return new UpdateChoreParticipationsCommand(
                action == UpdatedChoreParticipations.Action.ADD
                        ? List.of(CHORE_PUBLIC_ID)
                        : List.of(),
                action == UpdatedChoreParticipations.Action.REMOVE
                        ? List.of(CHORE_PUBLIC_ID)
                        : List.of(),
                scope,
                Map.of(CHORE_PUBLIC_ID, CHORE_VERSION)
        );
    }
}
