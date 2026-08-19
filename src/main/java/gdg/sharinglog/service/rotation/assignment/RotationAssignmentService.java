package gdg.sharinglog.service.rotation.assignment;

import static gdg.sharinglog.domain.rotation.AssignmentEndReason.SAME_OCCURRENCE_EXCLUSIONS;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;

import gdg.sharinglog.domain.GroupMember;
import gdg.sharinglog.domain.rotation.AssignmentEndReason;
import gdg.sharinglog.domain.rotation.AssignmentTrigger;
import gdg.sharinglog.domain.rotation.ChoreAssignmentAttempt;
import gdg.sharinglog.domain.rotation.ChoreEligibleMember;
import gdg.sharinglog.domain.rotation.ChoreOccurrence;
import gdg.sharinglog.domain.rotation.NoCandidateReason;
import gdg.sharinglog.domain.rotation.OccurrenceStatus;
import gdg.sharinglog.domain.rotation.RotationDecisionLog;
import gdg.sharinglog.repository.GroupMemberRepository;
import gdg.sharinglog.repository.SharingGroupRepository;
import gdg.sharinglog.repository.rotation.ChoreAssignmentAttemptRepository;
import gdg.sharinglog.repository.rotation.ChoreEligibleMemberRepository;
import gdg.sharinglog.repository.rotation.ChoreOccurrenceRepository;
import gdg.sharinglog.repository.rotation.OccurrenceEligibleMemberRepository;
import gdg.sharinglog.repository.rotation.RotationDecisionLogRepository;
import gdg.sharinglog.rotation.engine.RotationAssignmentEngine;
import gdg.sharinglog.rotation.engine.RotationAssignmentResult;
import gdg.sharinglog.rotation.engine.RotationCandidate;
import gdg.sharinglog.service.rotation.exception.OccurrenceNotFoundException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RotationAssignmentService {

    public static final String ALGORITHM_VERSION = "fair-random-v4";
    private final SharingGroupRepository sharingGroupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final ChoreOccurrenceRepository occurrenceRepository;
    private final ChoreAssignmentAttemptRepository assignmentRepository;
    private final ChoreEligibleMemberRepository enrollmentRepository;
    private final OccurrenceEligibleMemberRepository eligibilityRepository;
    private final RotationDecisionLogRepository decisionLogRepository;
    private final DecisionSeedGenerator seedGenerator;
    private final EntityManager entityManager;

    @Transactional
    public RotationAssignmentResult assign(
            Long occurrenceId,
            AssignmentTrigger trigger,
            java.time.Instant assignedAt
    ) {
        Objects.requireNonNull(occurrenceId, "회차 ID는 필수입니다.");
        Objects.requireNonNull(trigger, "배정 계기는 필수입니다.");
        Objects.requireNonNull(assignedAt, "배정 시각은 필수입니다.");
        Long groupId = occurrenceRepository.findGroupIdById(occurrenceId)
                .orElseThrow(() -> new OccurrenceNotFoundException(occurrenceId.toString()));
        sharingGroupRepository.findByIdForUpdate(groupId)
                .orElseThrow(() -> new IllegalStateException("회차의 그룹을 찾을 수 없습니다."));
        ChoreOccurrence occurrence = occurrenceRepository.findByIdForUpdate(occurrenceId)
                .orElseThrow(() -> new OccurrenceNotFoundException(occurrenceId.toString()));
        entityManager.refresh(occurrence, LockModeType.PESSIMISTIC_WRITE);

        if (occurrence.getStatus() != OccurrenceStatus.NEEDS_ATTENTION) {
            throw new IllegalStateException("관리 필요 상태의 회차만 새 담당자를 배정할 수 있습니다.");
        }

        List<GroupMember> groupMembers = groupMemberRepository.findAllByGroup_Id(
                occurrence.getChore().getGroup().getId()
        );
        Map<Long, Long> eligibleActivationGenerations =
                currentEligibleActivationGenerations(occurrence);
        Map<Long, Long> fairnessCredits = currentFairnessCredits(occurrence);
        Optional<Long> previousAssigneeId = previousAssigneeId(occurrence);
        Map<Long, GroupMember> membersById = new HashMap<>();

        List<RotationCandidate> candidates = groupMembers.stream()
                .peek(member -> membersById.put(member.getId(), member))
                .map(member -> candidate(
                        occurrence,
                        member,
                        belongsToSnapshot(member, eligibleActivationGenerations),
                        fairnessCredits.getOrDefault(member.getId(), 0L),
                        previousAssigneeId.filter(member.getId()::equals).isPresent()
                ))
                .toList();

        long decisionSeed = seedGenerator.nextSeed();
        RotationAssignmentResult result =
                new RotationAssignmentEngine(new Random(decisionSeed)).assign(candidates);
        int decisionSequence = Math.toIntExact(
                decisionLogRepository.countByOccurrence_Id(occurrence.getId()) + 1
        );
        String candidateSnapshot = CandidateAuditFormatter.snapshot(result.candidateSnapshot());
        String decisionSummary = CandidateAuditFormatter.summary(result.selectionReasons());

        if (result instanceof RotationAssignmentResult.Assigned assigned) {
            GroupMember selectedMember = Objects.requireNonNull(
                    membersById.get(assigned.selectedMembershipId()),
                    "선택된 멤버가 그룹 후보 목록에 없습니다."
            );
            validateSelectedMember(
                    occurrence,
                    selectedMember,
                    eligibleActivationGenerations
            );
            int sequenceNumber = Math.toIntExact(
                    assignmentRepository.countByOccurrence_Id(occurrence.getId()) + 1
            );
            ChoreAssignmentAttempt attempt = ChoreAssignmentAttempt.assigned(
                    occurrence,
                    selectedMember,
                    sequenceNumber,
                    trigger,
                    assignedAt,
                    ALGORITHM_VERSION,
                    decisionSeed,
                    candidateSnapshot,
                    decisionSummary
            );
            decisionLogRepository.save(RotationDecisionLog.assigned(
                    occurrence,
                    decisionSequence,
                    trigger,
                    selectedMember,
                    ALGORITHM_VERSION,
                    decisionSeed,
                    candidateSnapshot,
                    decisionSummary,
                    assignedAt
            ));
            assignmentRepository.save(attempt);
            occurrence.assign(attempt);
            occurrenceRepository.save(occurrence);
        } else if (result instanceof RotationAssignmentResult.NoCandidate noCandidate) {
            NoCandidateReason reason = NoCandidateReason.valueOf(noCandidate.reason().name());
            decisionLogRepository.save(RotationDecisionLog.noCandidate(
                    occurrence,
                    decisionSequence,
                    trigger,
                    reason,
                    ALGORITHM_VERSION,
                    decisionSeed,
                    candidateSnapshot,
                    decisionSummary,
                    assignedAt
            ));
            occurrence.recordNoCandidate(reason, assignedAt);
            occurrenceRepository.save(occurrence);
        } else {
            throw new IllegalStateException("지원하지 않는 배정 결과입니다.");
        }

        return result;
    }

    private void validateSelectedMember(
            ChoreOccurrence occurrence,
            GroupMember selectedMember,
            Map<Long, Long> eligibleActivationGenerations
    ) {
        if (!selectedMember.isActive()
                || !belongsToSnapshot(selectedMember, eligibleActivationGenerations)
                || assignmentRepository.existsByOccurrence_IdAndAssignee_IdAndEndReasonIn(
                        occurrence.getId(),
                        selectedMember.getId(),
                        SAME_OCCURRENCE_EXCLUSIONS
                )) {
            throw new IllegalStateException("배정 직전 검증에서 유효하지 않은 후보가 선택되었습니다.");
        }
    }

    private Map<Long, Long> currentEligibleActivationGenerations(
            ChoreOccurrence occurrence
    ) {
        return eligibilityRepository
                .findAllByOccurrence_IdAndSnapshotVersionOrderById(
                        occurrence.getId(),
                        occurrence.getEligibilitySnapshotVersion()
                )
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        snapshot -> snapshot.getMember().getId(),
                        snapshot -> snapshot.getMemberActivationGeneration()
                ));
    }

    private Map<Long, Long> currentFairnessCredits(ChoreOccurrence occurrence) {
        return enrollmentRepository.findAllByChore_IdOrderById(
                        occurrence.getChore().getId()
                )
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        enrollment -> enrollment.getMember().getId(),
                        ChoreEligibleMember::getFairnessCredit
                ));
    }

    private boolean belongsToSnapshot(
            GroupMember member,
            Map<Long, Long> eligibleActivationGenerations
    ) {
        return Optional.ofNullable(eligibleActivationGenerations.get(member.getId()))
                .filter(generation ->
                        generation.longValue() == member.getActivationGeneration())
                .isPresent();
    }

    private Optional<Long> previousAssigneeId(ChoreOccurrence occurrence) {
        LocalDate periodStart = occurrence.getPeriodStart();
        return occurrenceRepository
                .findAllNonCancelledBefore(
                        occurrence.getChore().getId(),
                        periodStart,
                        PageRequest.of(0, 1)
                )
                .stream()
                .findFirst()
                .flatMap(previous -> assignmentRepository
                        .findFirstByOccurrence_IdOrderBySequenceNumberDesc(previous.getId()))
                .map(attempt -> attempt.getAssignee().getId());
    }

    private RotationCandidate candidate(
            ChoreOccurrence occurrence,
            GroupMember member,
            boolean eligible,
            long fairnessCredit,
            boolean previousAssignee
    ) {
        long validSameChoreAssignmentCount = assignmentRepository
                .countValidAssignmentsForChoreAndMemberBeforePeriod(
                        occurrence.getChore().getId(),
                        occurrence.getPeriodStart(),
                        member.getId()
                );
        long validSameFrequencyAssignmentCount = assignmentRepository
                .countValidAssignmentsForFrequencyAndMemberBeforePeriod(
                        occurrence.getChore().getGroup().getId(),
                        occurrence.getFrequencySnapshot(),
                        occurrence.getPeriodStart(),
                        member.getId()
                );
        long activePeriodLoad = assignmentRepository.countActiveOrCompletedPeriodLoad(
                occurrence.getChore().getGroup().getId(),
                occurrence.getFrequencySnapshot(),
                occurrence.getPeriodStart(),
                member.getId()
        );
        boolean declinedOrSubstituted = assignmentRepository
                .existsByOccurrence_IdAndAssignee_IdAndEndReasonIn(
                        occurrence.getId(),
                        member.getId(),
                        SAME_OCCURRENCE_EXCLUSIONS
                );

        return new RotationCandidate(
                member.getId(),
                member.isActive(),
                eligible,
                declinedOrSubstituted,
                validSameChoreAssignmentCount,
                fairnessCredit,
                validSameFrequencyAssignmentCount,
                Math.toIntExact(activePeriodLoad),
                previousAssignee
        );
    }
}
