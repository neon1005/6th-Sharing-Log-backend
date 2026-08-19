package gdg.sharinglog.service.rotation.assignment;

import java.time.Instant;
import java.util.Objects;

import gdg.sharinglog.domain.GroupMember;
import gdg.sharinglog.domain.rotation.AssignmentTrigger;
import gdg.sharinglog.domain.rotation.ChoreAssignmentAttempt;
import gdg.sharinglog.domain.rotation.ChoreOccurrence;
import gdg.sharinglog.domain.rotation.RotationDecisionLog;
import gdg.sharinglog.repository.rotation.ChoreAssignmentAttemptRepository;
import gdg.sharinglog.repository.rotation.ChoreOccurrenceRepository;
import gdg.sharinglog.repository.rotation.RotationDecisionLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DirectAssignmentService {

    public static final String ALGORITHM_VERSION = "manual-action-v1";

    private final ChoreAssignmentAttemptRepository assignmentRepository;
    private final ChoreOccurrenceRepository occurrenceRepository;
    private final RotationDecisionLogRepository decisionLogRepository;

    @Transactional
    public ChoreAssignmentAttempt assign(
            ChoreOccurrence occurrence,
            GroupMember assignee,
            AssignmentTrigger trigger,
            Instant assignedAt,
            String reason
    ) {
        return assign(occurrence, assignee, trigger, assignedAt, reason, false);
    }

    @Transactional
    public ChoreAssignmentAttempt reopenCompleted(
            ChoreOccurrence occurrence,
            GroupMember assignee,
            Instant reopenedAt,
            String reason
    ) {
        return assign(
                occurrence,
                assignee,
                AssignmentTrigger.COMPLETION_REOPENED,
                reopenedAt,
                reason,
                true
        );
    }

    private ChoreAssignmentAttempt assign(
            ChoreOccurrence occurrence,
            GroupMember assignee,
            AssignmentTrigger trigger,
            Instant assignedAt,
            String reason,
            boolean reopenCompleted
    ) {
        ChoreOccurrence requiredOccurrence =
                Objects.requireNonNull(occurrence, "회차는 필수입니다.");
        GroupMember requiredAssignee =
                Objects.requireNonNull(assignee, "직접 배정 멤버는 필수입니다.");
        AssignmentTrigger requiredTrigger =
                Objects.requireNonNull(trigger, "직접 배정 계기는 필수입니다.");
        Instant effectiveAssignedAt =
                Objects.requireNonNull(assignedAt, "직접 배정 시각은 필수입니다.");
        String decisionSummary = requireText(reason);
        String candidateSnapshot = "membershipPublicId="
                + requiredAssignee.getPublicId()
                + "|decision=SELECTED_BY_USER_ACTION";
        int sequenceNumber = Math.toIntExact(
                assignmentRepository.countByOccurrence_Id(requiredOccurrence.getId()) + 1
        );
        int decisionSequence = Math.toIntExact(
                decisionLogRepository.countByOccurrence_Id(requiredOccurrence.getId()) + 1
        );

        ChoreAssignmentAttempt assignment = ChoreAssignmentAttempt.assigned(
                requiredOccurrence,
                requiredAssignee,
                sequenceNumber,
                requiredTrigger,
                effectiveAssignedAt,
                ALGORITHM_VERSION,
                0L,
                candidateSnapshot,
                decisionSummary
        );
        decisionLogRepository.save(RotationDecisionLog.assigned(
                requiredOccurrence,
                decisionSequence,
                requiredTrigger,
                requiredAssignee,
                ALGORITHM_VERSION,
                0L,
                candidateSnapshot,
                decisionSummary,
                effectiveAssignedAt
        ));
        assignmentRepository.save(assignment);
        if (reopenCompleted) {
            requiredOccurrence.reopenCompleted(assignment, effectiveAssignedAt);
        } else {
            requiredOccurrence.assign(assignment);
        }
        occurrenceRepository.saveAndFlush(requiredOccurrence);
        return assignment;
    }

    private String requireText(String reason) {
        String normalized = Objects.requireNonNull(reason, "직접 배정 사유는 필수입니다.").trim();
        if (normalized.isEmpty() || normalized.length() > 500) {
            throw new IllegalArgumentException("직접 배정 사유는 1~500자여야 합니다.");
        }
        return normalized;
    }
}
