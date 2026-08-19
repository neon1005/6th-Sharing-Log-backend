package gdg.sharinglog.web.rotation;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import gdg.sharinglog.domain.GroupMember;
import gdg.sharinglog.domain.User;
import gdg.sharinglog.domain.rotation.ChoreOccurrence;
import gdg.sharinglog.domain.rotation.OccurrenceStatus;
import gdg.sharinglog.domain.rotation.SubstituteRequest;
import gdg.sharinglog.repository.rotation.ChoreAssignmentAttemptRepository;
import gdg.sharinglog.repository.rotation.SubstituteRequestRepository;
import gdg.sharinglog.service.rotation.access.RotationActor;
import gdg.sharinglog.service.rotation.api.chore.ChoreView;
import gdg.sharinglog.web.rotation.dto.AttentionResponse;
import gdg.sharinglog.web.rotation.dto.ChoreResponse;
import gdg.sharinglog.web.rotation.dto.MemberRefResponse;
import gdg.sharinglog.web.rotation.dto.OccurrenceActionResponse;
import gdg.sharinglog.web.rotation.dto.OccurrenceSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RotationViewMapper {

    private final ChoreAssignmentAttemptRepository assignmentRepository;
    private final SubstituteRequestRepository substituteRequestRepository;

    public ChoreResponse chore(ChoreView view) {
        var chore = view.chore();
        return new ChoreResponse(
                chore.getPublicId(),
                chore.getGroup().getPublicId(),
                chore.getName(),
                new ChoreResponse.ScheduleResponse(
                        chore.getFrequency(),
                        chore.getDueTime(),
                        chore.getWeeklyDueDay(),
                        chore.getBiweeklyAnchorDate()
                ),
                new ChoreResponse.EligibilityResponse(
                        chore.getEligibilityMode(),
                        view.eligibleMembers().stream().map(this::member).toList()
                ),
                chore.isActive(),
                chore.getCreatedBy().getPublicId(),
                chore.getCreatedAt(),
                chore.getVersion()
        );
    }

    public OccurrenceSummaryResponse occurrence(
            ChoreOccurrence occurrence,
            RotationActor actor
    ) {
        return occurrence(occurrence, actor, occurrence.getChore().getName());
    }

    public OccurrenceSummaryResponse completedOccurrence(
            ChoreOccurrence occurrence,
            RotationActor actor
    ) {
        return occurrence(occurrence, actor, occurrence.getChoreNameSnapshot());
    }

    private OccurrenceSummaryResponse occurrence(
            ChoreOccurrence occurrence,
            RotationActor actor,
            String choreName
    ) {
        Optional<SubstituteRequest> activeSubstituteRequest =
                occurrence.getStatus() == OccurrenceStatus.ASSIGNED
                        ? substituteRequestRepository.findByOccurrence_IdAndActiveMarker(
                                occurrence.getId(),
                                1
                        )
                        : Optional.empty();
        return new OccurrenceSummaryResponse(
                occurrence.getPublicId(),
                occurrence.getChore().getPublicId(),
                choreName,
                occurrence.getFrequencySnapshot(),
                occurrence.getPeriodStart(),
                occurrence.getPeriodEndExclusive(),
                occurrence.getTimeZoneIdSnapshot(),
                occurrence.getDueAt(),
                occurrence.getStatus(),
                occurrence.currentAssignee().map(this::member).orElse(null),
                lastAssignee(occurrence),
                attention(occurrence),
                substituteRequestNotice(activeSubstituteRequest, actor),
                availableActions(occurrence, actor, activeSubstituteRequest),
                occurrence.getClosedAt(),
                occurrence.getVersion()
        );
    }

    public OccurrenceActionResponse.OccurrenceState actionState(ChoreOccurrence occurrence) {
        return new OccurrenceActionResponse.OccurrenceState(
                occurrence.getPublicId(),
                occurrence.getStatus(),
                occurrence.currentAssignee().map(this::member).orElse(null),
                lastAssignee(occurrence),
                attention(occurrence),
                occurrence.getClosedAt(),
                occurrence.getVersion()
        );
    }

    public MemberRefResponse member(GroupMember membership) {
        User user = membership.getUser();
        return new MemberRefResponse(
                membership.getPublicId(),
                firstText(user.getNickname(), user.getEmail(), user.getUsername()),
                null,
                membership.getStatus()
        );
    }

    private MemberRefResponse lastAssignee(ChoreOccurrence occurrence) {
        if (occurrence.getStatus() == OccurrenceStatus.ASSIGNED) {
            return null;
        }
        return assignmentRepository
                .findFirstByOccurrence_IdOrderBySequenceNumberDesc(occurrence.getId())
                .map(attempt -> member(attempt.getAssignee()))
                .orElse(null);
    }

    private AttentionResponse attention(ChoreOccurrence occurrence) {
        if (occurrence.getAttentionReason() == null) {
            return null;
        }
        return new AttentionResponse(
                occurrence.getAttentionReason(),
                occurrence.getAttentionSince(),
                occurrence.getLastDecisionAt()
        );
    }

    private List<OccurrenceSummaryResponse.AvailableAction> availableActions(
            ChoreOccurrence occurrence,
            RotationActor actor,
            Optional<SubstituteRequest> activeSubstituteRequest
    ) {
        List<OccurrenceSummaryResponse.AvailableAction> actions = new ArrayList<>();
        if (occurrence.getStatus() == OccurrenceStatus.ASSIGNED
                && occurrence.currentAssignee()
                .map(GroupMember::getId)
                .filter(actor.membership().getId()::equals)
                .isPresent()) {
            var activeOn = java.time.LocalDate.now(
                    java.time.ZoneId.of(occurrence.getTimeZoneIdSnapshot())
            );
            if (!occurrence.getPeriodStart().isAfter(activeOn)) {
                actions.add(OccurrenceSummaryResponse.AvailableAction.COMPLETE);
            }
            if (activeSubstituteRequest.isEmpty()) {
                actions.add(OccurrenceSummaryResponse.AvailableAction.REQUEST_SUBSTITUTE);
            }
        }
        if (occurrence.getStatus() == OccurrenceStatus.NEEDS_ATTENTION && actor.isOwner()) {
            actions.add(OccurrenceSummaryResponse.AvailableAction.RETRY_ASSIGNMENT);
        }
        if (occurrence.getStatus() == OccurrenceStatus.COMPLETED
                && assignmentRepository
                .findFirstByOccurrence_IdOrderBySequenceNumberDesc(occurrence.getId())
                .filter(assignment -> assignment.isEffectiveCompletion()
                        && assignment.getAssignee().getId()
                        .equals(actor.membership().getId()))
                .isPresent()) {
            actions.add(OccurrenceSummaryResponse.AvailableAction.UNDO_COMPLETE);
        }
        return List.copyOf(actions);
    }

    private String substituteRequestNotice(
            Optional<SubstituteRequest> activeSubstituteRequest,
            RotationActor actor
    ) {
        return activeSubstituteRequest
                .filter(request -> !request.requester().getId()
                        .equals(actor.membership().getId()))
                .map(request -> "다른 사용자가 올린 대타 요청입니다")
                .orElse(null);
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "알 수 없는 멤버";
    }
}
