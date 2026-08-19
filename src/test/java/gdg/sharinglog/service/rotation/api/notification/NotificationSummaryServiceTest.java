package gdg.sharinglog.service.rotation.api.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import gdg.sharinglog.domain.rotation.SubstituteRequestStatus;
import gdg.sharinglog.service.rotation.api.occurrence.OccurrenceQueryService;
import gdg.sharinglog.service.rotation.api.substitute.SubstituteRequestApplicationService;
import gdg.sharinglog.service.rotation.api.substitute.SubstituteRequestBox;
import gdg.sharinglog.web.rotation.dto.OccurrenceListResponse;
import gdg.sharinglog.web.rotation.dto.OccurrenceSummaryResponse;
import gdg.sharinglog.web.rotation.dto.SubstituteRequestListResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.core.user.OAuth2User;

@ExtendWith(MockitoExtension.class)
class NotificationSummaryServiceTest {

    @Mock
    OccurrenceQueryService occurrenceQueryService;

    @Mock
    SubstituteRequestApplicationService substituteRequestService;

    @InjectMocks
    NotificationSummaryService service;

    @Test
    void summarizeAddsDueSoonAndPendingSubstituteCounts() {
        String groupPublicId = "group-public-id";
        OAuth2User principal = mock(OAuth2User.class);
        OccurrenceListResponse dueSoonResponse = mock(OccurrenceListResponse.class);
        SubstituteRequestListResponse inboxResponse = mock(SubstituteRequestListResponse.class);

        when(occurrenceQueryService.findDueSoon(groupPublicId, "google", principal))
                .thenReturn(dueSoonResponse);
        when(dueSoonResponse.items())
                .thenReturn(List.of(mock(OccurrenceSummaryResponse.class), mock(OccurrenceSummaryResponse.class)));
        when(substituteRequestService.findAll(
                groupPublicId,
                "google",
                principal,
                SubstituteRequestBox.INBOX,
                SubstituteRequestStatus.PENDING
        )).thenReturn(inboxResponse);
        when(inboxResponse.totalCount()).thenReturn(3);

        var summary = service.summarize(groupPublicId, "google", principal);

        assertEquals(2, summary.dueSoonCount());
        assertEquals(3, summary.pendingSubstituteRequestCount());
        assertEquals(5, summary.unreadCount());
    }
}
