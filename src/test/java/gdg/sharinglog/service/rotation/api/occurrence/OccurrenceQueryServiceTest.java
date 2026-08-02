package gdg.sharinglog.service.rotation.api.occurrence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import gdg.sharinglog.domain.GroupMember;
import gdg.sharinglog.domain.SharingGroup;
import gdg.sharinglog.domain.User;
import gdg.sharinglog.domain.rotation.Chore;
import gdg.sharinglog.domain.rotation.ChoreOccurrence;
import gdg.sharinglog.domain.rotation.OccurrenceStatus;
import gdg.sharinglog.repository.rotation.ChoreAssignmentAttemptRepository;
import gdg.sharinglog.repository.rotation.ChoreOccurrenceRepository;
import gdg.sharinglog.service.rotation.access.RotationActor;
import gdg.sharinglog.service.rotation.access.RotationActorAccessService;
import gdg.sharinglog.web.rotation.RotationViewMapper;
import gdg.sharinglog.web.rotation.dto.OccurrenceSummaryResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.core.user.OAuth2User;

@ExtendWith(MockitoExtension.class)
class OccurrenceQueryServiceTest {

    @Mock
    RotationActorAccessService accessService;

    @Mock
    ChoreOccurrenceRepository occurrenceRepository;

    @Mock
    ChoreAssignmentAttemptRepository assignmentRepository;

    @Mock
    RotationViewMapper viewMapper;

    @InjectMocks
    OccurrenceQueryService service;

    @Test
    void currentRotationExcludesOccurrencesForInactiveChores() {
        String groupPublicId = "group-public-id";
        LocalDate activeOn = LocalDate.of(2026, 7, 30);
        OAuth2User principal = mock(OAuth2User.class);
        SharingGroup group = mock(SharingGroup.class);
        GroupMember membership = mock(GroupMember.class);
        User user = mock(User.class);
        RotationActor actor = new RotationActor(group, membership, user);
        Chore activeChore = mock(Chore.class);
        Chore inactiveChore = mock(Chore.class);
        ChoreOccurrence activeOccurrence = mock(ChoreOccurrence.class);
        ChoreOccurrence inactiveOccurrence = mock(ChoreOccurrence.class);
        OccurrenceSummaryResponse activeResponse = mock(OccurrenceSummaryResponse.class);

        when(accessService.requireActiveMember(
                groupPublicId,
                "google",
                principal
        )).thenReturn(actor);
        when(group.getId()).thenReturn(1L);
        when(group.getPublicId()).thenReturn(groupPublicId);
        when(group.getTimeZoneId()).thenReturn("Asia/Seoul");
        when(activeOccurrence.getChore()).thenReturn(activeChore);
        when(inactiveOccurrence.getChore()).thenReturn(inactiveChore);
        when(activeChore.isActive()).thenReturn(true);
        when(inactiveChore.isActive()).thenReturn(false);
        when(occurrenceRepository.findAllActiveOn(1L, activeOn))
                .thenReturn(List.of(inactiveOccurrence, activeOccurrence));
        when(viewMapper.occurrence(activeOccurrence, actor)).thenReturn(activeResponse);

        var response = service.findActiveOn(
                groupPublicId,
                "google",
                principal,
                null,
                activeOn,
                Set.of(),
                false,
                null
        );

        assertEquals(List.of(activeResponse), response.items());
        verify(viewMapper, never()).occurrence(inactiveOccurrence, actor);
    }

    @Test
    void findDueSoonReturnsCallersAssignedOccurrencesOrderedByRepository() {
        String groupPublicId = "group-public-id";
        OAuth2User principal = mock(OAuth2User.class);
        SharingGroup group = mock(SharingGroup.class);
        GroupMember membership = mock(GroupMember.class);
        User user = mock(User.class);
        RotationActor actor = new RotationActor(group, membership, user);
        ChoreOccurrence soonest = mock(ChoreOccurrence.class);
        ChoreOccurrence later = mock(ChoreOccurrence.class);
        OccurrenceSummaryResponse soonestResponse = mock(OccurrenceSummaryResponse.class);
        OccurrenceSummaryResponse laterResponse = mock(OccurrenceSummaryResponse.class);

        when(accessService.requireActiveMember(
                groupPublicId,
                "google",
                principal
        )).thenReturn(actor);
        when(group.getId()).thenReturn(1L);
        when(group.getPublicId()).thenReturn(groupPublicId);
        when(group.getTimeZoneId()).thenReturn("Asia/Seoul");
        when(group.timeZone()).thenReturn(java.time.ZoneId.of("Asia/Seoul"));
        when(membership.getId()).thenReturn(2L);
        when(occurrenceRepository
                .findAllByChore_Group_IdAndStatusAndCurrentAssignment_Assignee_IdOrderByDueAtAsc(
                        1L,
                        OccurrenceStatus.ASSIGNED,
                        2L
                ))
                .thenReturn(List.of(soonest, later));
        when(viewMapper.occurrence(soonest, actor)).thenReturn(soonestResponse);
        when(viewMapper.occurrence(later, actor)).thenReturn(laterResponse);

        var response = service.findDueSoon(groupPublicId, "google", principal);

        assertEquals(List.of(soonestResponse, laterResponse), response.items());
    }
}
