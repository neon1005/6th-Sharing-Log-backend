package gdg.sharinglog.service.rotation.api.occurrence;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import gdg.sharinglog.domain.GroupMember;
import gdg.sharinglog.domain.rotation.ChoreFrequency;
import gdg.sharinglog.domain.rotation.ChoreOccurrence;
import gdg.sharinglog.domain.rotation.OccurrenceStatus;
import gdg.sharinglog.repository.rotation.ChoreOccurrenceRepository;
import gdg.sharinglog.service.rotation.access.RotationActor;
import gdg.sharinglog.service.rotation.access.RotationActorAccessService;
import gdg.sharinglog.web.rotation.RotationViewMapper;
import gdg.sharinglog.web.rotation.dto.OccurrenceListResponse;
import gdg.sharinglog.web.rotation.dto.CompletedOccurrenceHistoryResponse;
import gdg.sharinglog.repository.rotation.ChoreAssignmentAttemptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OccurrenceQueryService {

    private final RotationActorAccessService accessService;
    private final ChoreOccurrenceRepository occurrenceRepository;
    private final ChoreAssignmentAttemptRepository assignmentRepository;
    private final RotationViewMapper viewMapper;

    @Transactional(readOnly = true)
    public OccurrenceListResponse findActiveOn(
            String groupPublicId,
            String registrationId,
            OAuth2User principal,
            ChoreFrequency frequency,
            LocalDate activeOn,
            Set<OccurrenceStatus> statuses,
            boolean mineOnly,
            String chorePublicId
    ) {
        RotationActor actor =
                accessService.requireActiveMember(groupPublicId, registrationId, principal);
        LocalDate effectiveDate = activeOn == null
                ? LocalDate.now(actor.group().timeZone())
                : activeOn;
        Set<OccurrenceStatus> effectiveStatuses =
                statuses == null ? Set.of() : Set.copyOf(statuses);

        List<ChoreOccurrence> occurrences = occurrenceRepository
                .findAllActiveOn(actor.group().getId(), effectiveDate)
                .stream()
                .filter(item -> item.getChore().isActive())
                .filter(item -> frequency == null || item.getFrequencySnapshot() == frequency)
                .filter(item -> effectiveStatuses.isEmpty()
                        || effectiveStatuses.contains(item.getStatus()))
                .filter(item -> chorePublicId == null
                        || item.getChore().getPublicId().equals(chorePublicId))
                .filter(item -> !mineOnly || isCurrentAssignee(item, actor.membership()))
                .toList();

        return new OccurrenceListResponse(
                actor.group().getPublicId(),
                frequency,
                new OccurrenceListResponse.QueryResponse(
                        effectiveDate,
                        actor.group().getTimeZoneId()
                ),
                occurrences.stream().map(item -> viewMapper.occurrence(item, actor)).toList(),
                null,
                false
        );
    }

    @Transactional(readOnly = true)
    public OccurrenceListResponse findDueSoon(
            String groupPublicId,
            String registrationId,
            OAuth2User principal
    ) {
        RotationActor actor =
                accessService.requireActiveMember(groupPublicId, registrationId, principal);
        List<ChoreOccurrence> occurrences = occurrenceRepository
                .findAllByChore_Group_IdAndStatusAndCurrentAssignment_Assignee_IdOrderByDueAtAsc(
                        actor.group().getId(),
                        OccurrenceStatus.ASSIGNED,
                        actor.membership().getId()
                );
        return new OccurrenceListResponse(
                actor.group().getPublicId(),
                null,
                new OccurrenceListResponse.QueryResponse(
                        LocalDate.now(actor.group().timeZone()),
                        actor.group().getTimeZoneId()
                ),
                occurrences.stream().map(item -> viewMapper.occurrence(item, actor)).toList(),
                null,
                false
        );
    }

    @Transactional(readOnly = true)
    public CompletedOccurrenceHistoryResponse findCompletedHistory(
            String groupPublicId,
            String registrationId,
            OAuth2User principal,
            boolean mineOnly,
            String chorePublicId
    ) {
        RotationActor actor =
                accessService.requireActiveMember(groupPublicId, registrationId, principal);
        List<ChoreOccurrence> completed = occurrenceRepository
                .findAllByChore_Group_IdAndStatusOrderByClosedAtDescIdDesc(
                        actor.group().getId(),
                        OccurrenceStatus.COMPLETED
                )
                .stream()
                .filter(item -> chorePublicId == null
                        || item.getChore().getPublicId().equals(chorePublicId))
                .filter(item -> !mineOnly || completedBy(item, actor.membership()))
                .toList();
        var items = completed.stream()
                .map(item -> viewMapper.completedOccurrence(item, actor))
                .toList();
        return new CompletedOccurrenceHistoryResponse(
                actor.group().getPublicId(),
                mineOnly,
                items,
                items.size()
        );
    }

    private boolean isCurrentAssignee(ChoreOccurrence occurrence, GroupMember actor) {
        return occurrence.getStatus() == OccurrenceStatus.ASSIGNED
                && occurrence.currentAssignee()
                .map(GroupMember::getId)
                .filter(actor.getId()::equals)
                .isPresent();
    }

    private boolean completedBy(ChoreOccurrence occurrence, GroupMember actor) {
        return assignmentRepository
                .findFirstByOccurrence_IdOrderBySequenceNumberDesc(occurrence.getId())
                .filter(assignment -> assignment.isEffectiveCompletion()
                        && assignment.getAssignee().getId().equals(actor.getId()))
                .isPresent();
    }
}
