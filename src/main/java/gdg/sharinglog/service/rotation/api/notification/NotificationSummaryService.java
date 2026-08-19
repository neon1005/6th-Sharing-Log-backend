package gdg.sharinglog.service.rotation.api.notification;

import gdg.sharinglog.domain.rotation.SubstituteRequestStatus;
import gdg.sharinglog.service.rotation.api.occurrence.OccurrenceQueryService;
import gdg.sharinglog.service.rotation.api.substitute.SubstituteRequestApplicationService;
import gdg.sharinglog.service.rotation.api.substitute.SubstituteRequestBox;
import gdg.sharinglog.web.rotation.dto.NotificationSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationSummaryService {

    private final OccurrenceQueryService occurrenceQueryService;
    private final SubstituteRequestApplicationService substituteRequestService;

    @Transactional(readOnly = true)
    public NotificationSummaryResponse summarize(
            String groupPublicId,
            String registrationId,
            OAuth2User principal
    ) {
        int dueSoonCount = occurrenceQueryService
                .findDueSoon(groupPublicId, registrationId, principal)
                .items()
                .size();
        int pendingSubstituteRequestCount = substituteRequestService
                .findAll(
                        groupPublicId,
                        registrationId,
                        principal,
                        SubstituteRequestBox.INBOX,
                        SubstituteRequestStatus.PENDING
                )
                .totalCount();
        return new NotificationSummaryResponse(
                dueSoonCount,
                pendingSubstituteRequestCount,
                dueSoonCount + pendingSubstituteRequestCount
        );
    }
}
