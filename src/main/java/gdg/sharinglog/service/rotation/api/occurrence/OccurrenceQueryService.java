package gdg.sharinglog.service.rotation.api.occurrence;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import gdg.sharinglog.domain.GroupMember;
import gdg.sharinglog.domain.rotation.ChoreFrequency;
import gdg.sharinglog.domain.rotation.ChoreOccurrence;
import gdg.sharinglog.domain.rotation.OccurrenceStatus;
import gdg.sharinglog.repository.rotation.ChoreOccurrenceRepository;
import gdg.sharinglog.service.rotation.access.RotationActor;
import gdg.sharinglog.service.rotation.access.RotationActorAccessService;
import gdg.sharinglog.service.rotation.occurrence.OccurrencePlanningHorizon;
import gdg.sharinglog.web.rotation.RotationViewMapper;
import gdg.sharinglog.web.rotation.dto.OccurrenceListResponse;
import gdg.sharinglog.web.rotation.dto.CompletedOccurrenceHistoryResponse;
import gdg.sharinglog.web.rotation.dto.OccurrenceWeekPreviewResponse;
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
        return findDueSoon(groupPublicId, registrationId, principal, Instant.now());
    }

    OccurrenceListResponse findDueSoon(
            String groupPublicId,
            String registrationId,
            OAuth2User principal,
            Instant referenceTime
    ) {
        RotationActor actor =
                accessService.requireActiveMember(groupPublicId, registrationId, principal);
        Instant now = Objects.requireNonNull(referenceTime, "기준 시각은 필수입니다.");
        LocalDate activeOn = now.atZone(actor.group().timeZone()).toLocalDate();
        int maximumHoursBeforeDue = maximumHoursBeforeDue(actor.membership());
        List<ChoreOccurrence> occurrences = occurrenceRepository
                .findAllAssignedToMemberDueBetween(
                        actor.group().getId(),
                        actor.membership().getId(),
                        now,
                        now.plus(maximumHoursBeforeDue, ChronoUnit.HOURS)
                )
                .stream()
                .filter(item -> isDueSoon(item, actor.membership(), now))
                .toList();
        return new OccurrenceListResponse(
                actor.group().getPublicId(),
                null,
                new OccurrenceListResponse.QueryResponse(
                        activeOn,
                        actor.group().getTimeZoneId()
                ),
                occurrences.stream().map(item -> viewMapper.occurrence(item, actor)).toList(),
                null,
                false
        );
    }

    @Transactional(readOnly = true)
    public OccurrenceWeekPreviewResponse findWeeklyPreview(
            String groupPublicId,
            String registrationId,
            OAuth2User principal,
            int weekOffset,
            ChoreFrequency frequency
    ) {
        RotationActor actor =
                accessService.requireActiveMember(groupPublicId, registrationId, principal);
        LocalDate activeOn = LocalDate.now(actor.group().timeZone());
        var window = OccurrencePlanningHorizon.weekWindow(
                actor.group(),
                activeOn,
                weekOffset
        );
        List<ChoreOccurrence> occurrences = occurrenceRepository
                .findAllPlannedOverlapping(
                        actor.group().getId(),
                        window.fromInclusive(),
                        window.toExclusive()
                )
                .stream()
                .filter(item -> frequency == null || item.getFrequencySnapshot() == frequency)
                .toList();

        return new OccurrenceWeekPreviewResponse(
                actor.group().getPublicId(),
                frequency,
                weekOffset,
                window.fromInclusive(),
                window.toExclusive(),
                actor.group().getTimeZoneId(),
                occurrences.stream()
                        .map(item -> viewMapper.occurrence(item, actor))
                        .toList()
        );
    }

    private int maximumHoursBeforeDue(GroupMember membership) {
        return Math.max(
                membership.getDailyDueSoonHours(),
                Math.max(
                        membership.getWeeklyDueSoonHours(),
                        membership.getBiweeklyDueSoonHours()
                )
        );
    }

    private boolean isDueSoon(
            ChoreOccurrence occurrence,
            GroupMember membership,
            Instant referenceTime
    ) {
        Instant dueAt = occurrence.getDueAt();
        if (dueAt.isBefore(referenceTime)) {
            return false;
        }
        int hoursBeforeDue = switch (occurrence.getFrequencySnapshot()) {
            case DAILY -> membership.getDailyDueSoonHours();
            case WEEKLY -> membership.getWeeklyDueSoonHours();
            case BIWEEKLY -> membership.getBiweeklyDueSoonHours();
        };
        return !dueAt.isAfter(referenceTime.plus(hoursBeforeDue, ChronoUnit.HOURS));
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
