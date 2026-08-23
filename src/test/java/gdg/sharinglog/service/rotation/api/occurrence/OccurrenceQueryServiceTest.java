package gdg.sharinglog.service.rotation.api.occurrence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import gdg.sharinglog.domain.GroupMember;
import gdg.sharinglog.domain.SharingGroup;
import gdg.sharinglog.domain.User;
import gdg.sharinglog.domain.rotation.Chore;
import gdg.sharinglog.domain.rotation.ChoreOccurrence;
import gdg.sharinglog.domain.rotation.ChoreFrequency;
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
    void findDueSoonUsesCurrentMembershipsFrequencySpecificLeadTimes() {
        String groupPublicId = "group-public-id";
        Instant referenceTime = Instant.parse("2026-08-16T03:00:00Z");
        OAuth2User principal = mock(OAuth2User.class);
        SharingGroup group = mock(SharingGroup.class);
        GroupMember membership = mock(GroupMember.class);
        User user = mock(User.class);
        RotationActor actor = new RotationActor(group, membership, user);
        ChoreOccurrence dailyWithinFiveHours = mock(ChoreOccurrence.class);
        ChoreOccurrence weeklyWithinTenHours = mock(ChoreOccurrence.class);
        ChoreOccurrence biweeklyWithinTwentyHours = mock(ChoreOccurrence.class);
        ChoreOccurrence dailyOutsideFiveHours = mock(ChoreOccurrence.class);
        ChoreOccurrence overdueBiweekly = mock(ChoreOccurrence.class);
        OccurrenceSummaryResponse dailyResponse = mock(OccurrenceSummaryResponse.class);
        OccurrenceSummaryResponse weeklyResponse = mock(OccurrenceSummaryResponse.class);
        OccurrenceSummaryResponse biweeklyResponse = mock(OccurrenceSummaryResponse.class);

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
        when(membership.getDailyDueSoonHours()).thenReturn(5);
        when(membership.getWeeklyDueSoonHours()).thenReturn(10);
        when(membership.getBiweeklyDueSoonHours()).thenReturn(20);
        when(dailyWithinFiveHours.getFrequencySnapshot()).thenReturn(ChoreFrequency.DAILY);
        when(dailyWithinFiveHours.getDueAt()).thenReturn(referenceTime.plusSeconds(4 * 3600));
        when(weeklyWithinTenHours.getFrequencySnapshot()).thenReturn(ChoreFrequency.WEEKLY);
        when(weeklyWithinTenHours.getDueAt()).thenReturn(referenceTime.plusSeconds(8 * 3600));
        when(biweeklyWithinTwentyHours.getFrequencySnapshot())
                .thenReturn(ChoreFrequency.BIWEEKLY);
        when(biweeklyWithinTwentyHours.getDueAt())
                .thenReturn(referenceTime.plusSeconds(15 * 3600));
        when(dailyOutsideFiveHours.getFrequencySnapshot()).thenReturn(ChoreFrequency.DAILY);
        when(dailyOutsideFiveHours.getDueAt()).thenReturn(referenceTime.plusSeconds(6 * 3600));
        when(overdueBiweekly.getDueAt()).thenReturn(referenceTime.minusSeconds(1));
        when(occurrenceRepository
                .findAllAssignedToMemberDueBetween(
                        1L,
                        2L,
                        referenceTime,
                        referenceTime.plusSeconds(20 * 3600)
                ))
                .thenReturn(List.of(
                        dailyWithinFiveHours,
                        weeklyWithinTenHours,
                        biweeklyWithinTwentyHours,
                        dailyOutsideFiveHours,
                        overdueBiweekly
                ));
        when(viewMapper.occurrence(dailyWithinFiveHours, actor)).thenReturn(dailyResponse);
        when(viewMapper.occurrence(weeklyWithinTenHours, actor)).thenReturn(weeklyResponse);
        when(viewMapper.occurrence(biweeklyWithinTwentyHours, actor))
                .thenReturn(biweeklyResponse);

        var response = service.findDueSoon(
                groupPublicId,
                "google",
                principal,
                referenceTime
        );

        assertEquals(
                List.of(dailyResponse, weeklyResponse, biweeklyResponse),
                response.items()
        );
        verify(viewMapper, never()).occurrence(dailyOutsideFiveHours, actor);
        verify(viewMapper, never()).occurrence(overdueBiweekly, actor);
    }

    @Test
    void weeklyPreviewReturnsTheRequestedSevenDayWindow() {
        String groupPublicId = "group-public-id";
        OAuth2User principal = mock(OAuth2User.class);
        SharingGroup group = mock(SharingGroup.class);
        GroupMember membership = mock(GroupMember.class);
        User user = mock(User.class);
        RotationActor actor = new RotationActor(group, membership, user);
        ChoreOccurrence occurrence = mock(ChoreOccurrence.class);
        OccurrenceSummaryResponse mapped = mock(OccurrenceSummaryResponse.class);
        var zone = java.time.ZoneId.of("Asia/Seoul");
        LocalDate today = LocalDate.now(zone);
        int daysSinceMonday = Math.floorMod(
                today.getDayOfWeek().getValue() - DayOfWeek.MONDAY.getValue(),
                7
        );
        LocalDate fromInclusive = today.minusDays(daysSinceMonday).plusWeeks(4);
        LocalDate toExclusive = fromInclusive.plusWeeks(1);

        when(accessService.requireActiveMember(groupPublicId, "google", principal))
                .thenReturn(actor);
        when(group.getId()).thenReturn(1L);
        when(group.getPublicId()).thenReturn(groupPublicId);
        when(group.getTimeZoneId()).thenReturn(zone.getId());
        when(group.timeZone()).thenReturn(zone);
        when(group.getWeekStartsOn()).thenReturn(DayOfWeek.MONDAY);
        when(occurrence.getFrequencySnapshot()).thenReturn(ChoreFrequency.WEEKLY);
        when(occurrenceRepository.findAllPlannedOverlapping(
                1L,
                fromInclusive,
                toExclusive
        )).thenReturn(List.of(occurrence));
        when(viewMapper.occurrence(occurrence, actor)).thenReturn(mapped);

        var response = service.findWeeklyPreview(
                groupPublicId,
                "google",
                principal,
                4,
                ChoreFrequency.WEEKLY
        );

        assertEquals(4, response.weekOffset());
        assertEquals(fromInclusive, response.fromInclusive());
        assertEquals(toExclusive, response.toExclusive());
        assertEquals(List.of(mapped), response.items());
    }

    @Test
    void completedHistoryDoesNotQueryOverdueOccurrencesByDefault() {
        String groupPublicId = "group-public-id";
        OAuth2User principal = mock(OAuth2User.class);
        SharingGroup group = mock(SharingGroup.class);
        GroupMember membership = mock(GroupMember.class);
        User user = mock(User.class);
        RotationActor actor = new RotationActor(group, membership, user);

        when(accessService.requireActiveMember(groupPublicId, "google", principal))
                .thenReturn(actor);
        when(group.getId()).thenReturn(1L);
        when(group.getPublicId()).thenReturn(groupPublicId);
        when(occurrenceRepository.findAllByChore_Group_IdAndStatusOrderByClosedAtDescIdDesc(
                1L,
                OccurrenceStatus.COMPLETED
        )).thenReturn(List.of());

        service.findCompletedHistory(
                groupPublicId,
                "google",
                principal,
                false,
                null,
                false
        );

        verify(occurrenceRepository, never())
                .findAllOverdueAssignedByGroupId(eq(1L), any());
    }

    @Test
    void completedHistoryIncludesOverdueAssignedOccurrencesWhenRequested() {
        String groupPublicId = "group-public-id";
        OAuth2User principal = mock(OAuth2User.class);
        SharingGroup group = mock(SharingGroup.class);
        GroupMember membership = mock(GroupMember.class);
        User user = mock(User.class);
        RotationActor actor = new RotationActor(group, membership, user);
        ChoreOccurrence completedOccurrence = mock(ChoreOccurrence.class);
        ChoreOccurrence overdueOccurrence = mock(ChoreOccurrence.class);
        OccurrenceSummaryResponse completedResponse = mock(OccurrenceSummaryResponse.class);
        OccurrenceSummaryResponse overdueResponse = mock(OccurrenceSummaryResponse.class);

        when(accessService.requireActiveMember(groupPublicId, "google", principal))
                .thenReturn(actor);
        when(group.getId()).thenReturn(1L);
        when(group.getPublicId()).thenReturn(groupPublicId);
        when(completedOccurrence.getClosedAt()).thenReturn(Instant.parse("2026-08-19T00:00:00Z"));
        when(overdueOccurrence.getClosedAt()).thenReturn(null);
        when(overdueOccurrence.getDueAt()).thenReturn(Instant.parse("2026-08-20T00:00:00Z"));
        when(occurrenceRepository.findAllByChore_Group_IdAndStatusOrderByClosedAtDescIdDesc(
                1L,
                OccurrenceStatus.COMPLETED
        )).thenReturn(List.of(completedOccurrence));
        when(occurrenceRepository.findAllOverdueAssignedByGroupId(eq(1L), any()))
                .thenReturn(List.of(overdueOccurrence));
        when(viewMapper.completedOccurrence(completedOccurrence, actor))
                .thenReturn(completedResponse);
        when(viewMapper.completedOccurrence(overdueOccurrence, actor))
                .thenReturn(overdueResponse);

        var response = service.findCompletedHistory(
                groupPublicId,
                "google",
                principal,
                false,
                null,
                true
        );

        assertEquals(2, response.totalCount());
        assertEquals(List.of(overdueResponse, completedResponse), response.items());
    }

    @Test
    void completedHistoryMineOnlyFiltersOverdueOccurrencesByCurrentAssignee() {
        String groupPublicId = "group-public-id";
        OAuth2User principal = mock(OAuth2User.class);
        SharingGroup group = mock(SharingGroup.class);
        GroupMember membership = mock(GroupMember.class);
        GroupMember otherMembership = mock(GroupMember.class);
        User user = mock(User.class);
        RotationActor actor = new RotationActor(group, membership, user);
        ChoreOccurrence mine = mock(ChoreOccurrence.class);
        ChoreOccurrence someoneElses = mock(ChoreOccurrence.class);
        OccurrenceSummaryResponse mineResponse = mock(OccurrenceSummaryResponse.class);

        when(accessService.requireActiveMember(groupPublicId, "google", principal))
                .thenReturn(actor);
        when(group.getId()).thenReturn(1L);
        when(group.getPublicId()).thenReturn(groupPublicId);
        when(membership.getId()).thenReturn(10L);
        when(otherMembership.getId()).thenReturn(11L);
        when(mine.getStatus()).thenReturn(OccurrenceStatus.ASSIGNED);
        when(mine.currentAssignee()).thenReturn(java.util.Optional.of(membership));
        when(someoneElses.getStatus()).thenReturn(OccurrenceStatus.ASSIGNED);
        when(someoneElses.currentAssignee()).thenReturn(java.util.Optional.of(otherMembership));
        when(occurrenceRepository.findAllByChore_Group_IdAndStatusOrderByClosedAtDescIdDesc(
                1L,
                OccurrenceStatus.COMPLETED
        )).thenReturn(List.of());
        when(occurrenceRepository.findAllOverdueAssignedByGroupId(eq(1L), any()))
                .thenReturn(List.of(mine, someoneElses));
        when(viewMapper.completedOccurrence(mine, actor)).thenReturn(mineResponse);

        var response = service.findCompletedHistory(
                groupPublicId,
                "google",
                principal,
                true,
                null,
                true
        );

        assertEquals(List.of(mineResponse), response.items());
        verify(viewMapper, never()).completedOccurrence(someoneElses, actor);
    }
}
