package gdg.sharinglog.service.push;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import gdg.sharinglog.domain.GroupMember;
import gdg.sharinglog.domain.User;
import gdg.sharinglog.domain.rotation.ChoreOccurrence;
import gdg.sharinglog.repository.rotation.ChoreOccurrenceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DueSoonPushSchedulerTest {

    @Mock
    ChoreOccurrenceRepository occurrenceRepository;

    @Mock
    PushNotifier pushNotifier;

    @InjectMocks
    DueSoonPushScheduler scheduler;

    @Test
    void sendsA24HourReminderToAssigneeWithDueSoonEnabled() {
        ChoreOccurrence occurrence = mock(ChoreOccurrence.class);
        GroupMember assignee = mock(GroupMember.class);
        User user = mock(User.class);

        when(occurrenceRepository.findAllNeedingDueSoon24hNotification(any(), any()))
                .thenReturn(List.of(occurrence));
        when(occurrenceRepository.findAllNeedingDueSoon3hNotification(any(), any()))
                .thenReturn(List.of());
        when(occurrence.currentAssignee()).thenReturn(Optional.of(assignee));
        when(assignee.getUser()).thenReturn(user);
        when(user.isDueSoonPushEnabled()).thenReturn(true);
        when(user.getId()).thenReturn(1L);
        when(occurrence.getChoreNameSnapshot()).thenReturn("쓰레기 버리기");

        scheduler.sendDueSoonReminders();

        verify(pushNotifier).notifyUser(eq(1L), eq("마감 임박"), contains("24시간"), eq("/notification"));
        verify(occurrence).markDueSoon24hNotified(any());
    }

    @Test
    void skipsPushButStillMarksNotifiedWhenPreferenceDisabled() {
        ChoreOccurrence occurrence = mock(ChoreOccurrence.class);
        GroupMember assignee = mock(GroupMember.class);
        User user = mock(User.class);

        when(occurrenceRepository.findAllNeedingDueSoon24hNotification(any(), any()))
                .thenReturn(List.of(occurrence));
        when(occurrenceRepository.findAllNeedingDueSoon3hNotification(any(), any()))
                .thenReturn(List.of());
        when(occurrence.currentAssignee()).thenReturn(Optional.of(assignee));
        when(assignee.getUser()).thenReturn(user);
        when(user.isDueSoonPushEnabled()).thenReturn(false);

        scheduler.sendDueSoonReminders();

        verify(pushNotifier, never()).notifyUser(any(), any(), any(), any());
        verify(occurrence).markDueSoon24hNotified(any());
    }

    @Test
    void continuesProcessingWhenOneOccurrenceFails() {
        ChoreOccurrence failing = mock(ChoreOccurrence.class);
        ChoreOccurrence succeeding = mock(ChoreOccurrence.class);
        GroupMember assignee = mock(GroupMember.class);
        User user = mock(User.class);

        when(occurrenceRepository.findAllNeedingDueSoon24hNotification(any(), any()))
                .thenReturn(List.of());
        when(occurrenceRepository.findAllNeedingDueSoon3hNotification(any(), any()))
                .thenReturn(List.of(failing, succeeding));
        when(failing.currentAssignee()).thenThrow(new RuntimeException("boom"));
        when(succeeding.currentAssignee()).thenReturn(Optional.of(assignee));
        when(assignee.getUser()).thenReturn(user);
        when(user.isDueSoonPushEnabled()).thenReturn(true);
        when(user.getId()).thenReturn(2L);
        when(succeeding.getChoreNameSnapshot()).thenReturn("설거지");

        scheduler.sendDueSoonReminders();

        verify(succeeding).markDueSoon3hNotified(any());
        verify(failing, never()).markDueSoon3hNotified(any());
    }
}
