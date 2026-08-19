package gdg.sharinglog.service.rotation.substitute;

import java.time.Instant;

import gdg.sharinglog.domain.GroupMember;
import gdg.sharinglog.domain.rotation.ChoreOccurrence;
import gdg.sharinglog.domain.rotation.SubstituteRecipientStatus;
import gdg.sharinglog.repository.rotation.SubstituteRequestRecipientRepository;
import gdg.sharinglog.repository.rotation.SubstituteRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SubstituteRequestLifecycleService {

    private final SubstituteRequestRepository requestRepository;
    private final SubstituteRequestRecipientRepository recipientRepository;

    @Transactional
    public void cancelPendingForOccurrence(
            ChoreOccurrence occurrence,
            Instant cancelledAt
    ) {
        requestRepository.findByOccurrence_IdAndActiveMarker(occurrence.getId(), 1)
                .ifPresent(request -> {
                    recipientRepository.findAllByRequest_IdOrderById(request.getId())
                            .forEach(recipient -> recipient.markIneligible(cancelledAt));
                    request.cancel(cancelledAt);
                    requestRepository.save(request);
                });
    }

    @Transactional
    public void invalidatePendingForMember(
            GroupMember member,
            Instant changedAt
    ) {
        recipientRepository.findAllByMember_IdAndResponseStatus(
                        member.getId(),
                        SubstituteRecipientStatus.PENDING
                )
                .forEach(recipient -> {
                    recipient.markIneligible(changedAt);
                    exhaustIfNoPending(recipient.getRequest(), changedAt);
                });
    }

    @Transactional
    public void invalidatePendingForOccurrenceAndMember(
            ChoreOccurrence occurrence,
            GroupMember member,
            Instant changedAt
    ) {
        requestRepository.findByOccurrence_IdAndActiveMarker(occurrence.getId(), 1)
                .flatMap(request -> recipientRepository.findForUpdate(
                        request.getId(),
                        member.getId()
                ))
                .ifPresent(recipient -> {
                    recipient.markIneligible(changedAt);
                    exhaustIfNoPending(recipient.getRequest(), changedAt);
                });
    }

    private void exhaustIfNoPending(
            gdg.sharinglog.domain.rotation.SubstituteRequest request,
            Instant changedAt
    ) {
        recipientRepository.flush();
        boolean hasPending = recipientRepository
                .findAllByRequest_IdOrderById(request.getId())
                .stream()
                .anyMatch(candidate -> candidate.getResponseStatus().isPending());
        if (!hasPending && request.getStatus().isPending()) {
            request.exhaust(changedAt);
            requestRepository.save(request);
        }
    }
}
