package gdg.sharinglog.web.rotation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import gdg.sharinglog.domain.GroupMember;
import gdg.sharinglog.domain.SharingGroup;
import gdg.sharinglog.domain.User;
import gdg.sharinglog.domain.rotation.Chore;
import gdg.sharinglog.domain.rotation.ChoreOccurrence;
import gdg.sharinglog.domain.rotation.OccurrenceStatus;
import gdg.sharinglog.domain.rotation.SubstituteRequest;
import gdg.sharinglog.repository.rotation.ChoreAssignmentAttemptRepository;
import gdg.sharinglog.repository.rotation.SubstituteRequestRepository;
import gdg.sharinglog.service.rotation.access.RotationActor;
import gdg.sharinglog.web.rotation.dto.OccurrenceSummaryResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RotationViewMapperTest {

    @Mock
    ChoreAssignmentAttemptRepository assignmentRepository;

    @Mock
    SubstituteRequestRepository substituteRequestRepository;

    @InjectMocks
    RotationViewMapper mapper;

    @Test
    void currentOccurrenceUsesCurrentChoreNameAndOnlyCurrentWireframeActions() {
        ChoreOccurrence occurrence = org.mockito.Mockito.mock(ChoreOccurrence.class);
        Chore chore = org.mockito.Mockito.mock(Chore.class);
        SharingGroup group = org.mockito.Mockito.mock(SharingGroup.class);
        GroupMember membership = org.mockito.Mockito.mock(GroupMember.class);
        User user = org.mockito.Mockito.mock(User.class);
        RotationActor actor = new RotationActor(group, membership, user);

        when(occurrence.getId()).thenReturn(20L);
        when(occurrence.getChore()).thenReturn(chore);
        when(chore.getName()).thenReturn("변경된 업무명");
        when(occurrence.getStatus()).thenReturn(OccurrenceStatus.ASSIGNED);
        when(occurrence.getTimeZoneIdSnapshot()).thenReturn("Asia/Seoul");
        when(occurrence.getPeriodStart()).thenReturn(java.time.LocalDate.now(
                java.time.ZoneId.of("Asia/Seoul")
        ));
        when(occurrence.currentAssignee()).thenReturn(Optional.of(membership));
        when(membership.getId()).thenReturn(10L);
        when(membership.getUser()).thenReturn(user);
        when(substituteRequestRepository.findByOccurrence_IdAndActiveMarker(20L, 1))
                .thenReturn(Optional.empty());

        OccurrenceSummaryResponse response = mapper.occurrence(occurrence, actor);

        assertEquals("변경된 업무명", response.choreName());
        assertEquals(
                List.of(
                        OccurrenceSummaryResponse.AvailableAction.COMPLETE,
                        OccurrenceSummaryResponse.AvailableAction.REQUEST_SUBSTITUTE
                ),
                response.availableActions()
        );
    }

    @Test
    void futureOccurrenceOffersSubstituteRequestButNotCompletion() {
        ChoreOccurrence occurrence = org.mockito.Mockito.mock(ChoreOccurrence.class);
        Chore chore = org.mockito.Mockito.mock(Chore.class);
        SharingGroup group = org.mockito.Mockito.mock(SharingGroup.class);
        GroupMember membership = org.mockito.Mockito.mock(GroupMember.class);
        User user = org.mockito.Mockito.mock(User.class);
        RotationActor actor = new RotationActor(group, membership, user);

        when(occurrence.getId()).thenReturn(21L);
        when(occurrence.getChore()).thenReturn(chore);
        when(chore.getName()).thenReturn("미래 업무");
        when(occurrence.getStatus()).thenReturn(OccurrenceStatus.ASSIGNED);
        when(occurrence.getTimeZoneIdSnapshot()).thenReturn("Asia/Seoul");
        when(occurrence.getPeriodStart()).thenReturn(java.time.LocalDate.now(
                java.time.ZoneId.of("Asia/Seoul")
        ).plusDays(1));
        when(occurrence.currentAssignee()).thenReturn(Optional.of(membership));
        when(membership.getId()).thenReturn(10L);
        when(membership.getUser()).thenReturn(user);
        when(substituteRequestRepository.findByOccurrence_IdAndActiveMarker(21L, 1))
                .thenReturn(Optional.empty());

        OccurrenceSummaryResponse response = mapper.occurrence(occurrence, actor);

        assertEquals(
                List.of(OccurrenceSummaryResponse.AvailableAction.REQUEST_SUBSTITUTE),
                response.availableActions()
        );
    }

    @Test
    void pendingRequestFromAnotherMemberShowsNoticeAndNoRequestAction() {
        ChoreOccurrence occurrence = org.mockito.Mockito.mock(ChoreOccurrence.class);
        Chore chore = org.mockito.Mockito.mock(Chore.class);
        SharingGroup group = org.mockito.Mockito.mock(SharingGroup.class);
        GroupMember actorMembership = org.mockito.Mockito.mock(GroupMember.class);
        GroupMember requester = org.mockito.Mockito.mock(GroupMember.class);
        User actorUser = org.mockito.Mockito.mock(User.class);
        User requesterUser = org.mockito.Mockito.mock(User.class);
        SubstituteRequest request = org.mockito.Mockito.mock(SubstituteRequest.class);
        RotationActor actor = new RotationActor(group, actorMembership, actorUser);

        when(occurrence.getId()).thenReturn(22L);
        when(occurrence.getChore()).thenReturn(chore);
        when(chore.getName()).thenReturn("대타 요청 중인 업무");
        when(occurrence.getStatus()).thenReturn(OccurrenceStatus.ASSIGNED);
        when(occurrence.currentAssignee()).thenReturn(Optional.of(requester));
        when(actorMembership.getId()).thenReturn(10L);
        when(requester.getId()).thenReturn(11L);
        when(requester.getUser()).thenReturn(requesterUser);
        when(request.requester()).thenReturn(requester);
        when(substituteRequestRepository.findByOccurrence_IdAndActiveMarker(22L, 1))
                .thenReturn(Optional.of(request));

        OccurrenceSummaryResponse response = mapper.occurrence(occurrence, actor);

        assertEquals("다른 사용자가 올린 대타 요청입니다",
                response.substituteRequestNotice());
        assertEquals(List.of(), response.availableActions());
    }

    @Test
    void completedHistoryKeepsOccurrenceNameSnapshot() {
        ChoreOccurrence occurrence = org.mockito.Mockito.mock(ChoreOccurrence.class);
        Chore chore = org.mockito.Mockito.mock(Chore.class);
        SharingGroup group = org.mockito.Mockito.mock(SharingGroup.class);
        GroupMember membership = org.mockito.Mockito.mock(GroupMember.class);
        User user = org.mockito.Mockito.mock(User.class);
        RotationActor actor = new RotationActor(group, membership, user);

        when(occurrence.getChore()).thenReturn(chore);
        when(occurrence.getChoreNameSnapshot()).thenReturn("완료 당시 업무명");
        when(occurrence.getStatus()).thenReturn(OccurrenceStatus.ASSIGNED);
        when(occurrence.currentAssignee()).thenReturn(Optional.empty());

        OccurrenceSummaryResponse response = mapper.completedOccurrence(occurrence, actor);

        assertEquals("완료 당시 업무명", response.choreName());
    }
}
