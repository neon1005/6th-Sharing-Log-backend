package gdg.sharinglog.service.rotation.occurrence;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import gdg.sharinglog.domain.GroupMember;
import gdg.sharinglog.domain.rotation.AssignmentTrigger;
import gdg.sharinglog.domain.rotation.Chore;
import gdg.sharinglog.domain.rotation.ChoreOccurrence;
import gdg.sharinglog.domain.rotation.OccurrenceEligibleMember;
import gdg.sharinglog.repository.SharingGroupRepository;
import gdg.sharinglog.repository.rotation.ChoreOccurrenceRepository;
import gdg.sharinglog.repository.rotation.ChoreRepository;
import gdg.sharinglog.repository.rotation.OccurrenceEligibleMemberRepository;
import gdg.sharinglog.service.rotation.assignment.RotationAssignmentService;
import gdg.sharinglog.service.rotation.enrollment.ChoreEnrollmentService;
import gdg.sharinglog.service.rotation.exception.ChoreNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OccurrenceGenerationService {

    private final SharingGroupRepository sharingGroupRepository;
    private final ChoreRepository choreRepository;
    private final ChoreOccurrenceRepository occurrenceRepository;
    private final OccurrenceEligibleMemberRepository occurrenceEligibleMemberRepository;
    private final ChoreOccurrenceScheduleResolver scheduleResolver;
    private final ChoreEnrollmentService enrollmentService;
    private final RotationAssignmentService assignmentService;

    @Transactional
    public ChoreOccurrence ensureCurrentOccurrence(Long choreId, Instant referenceInstant) {
        Instant effectiveReference = Objects.requireNonNull(
                referenceInstant,
                "기준 시각은 필수입니다."
        );
        Chore chore = lockedActiveChore(choreId);
        OccurrenceSchedule schedule = scheduleResolver.resolve(chore, effectiveReference);
        return ensureOccurrence(chore, schedule, effectiveReference);
    }

    @Transactional
    public List<ChoreOccurrence> ensureOccurrencesUntil(
            Long choreId,
            Instant generatedAt,
            LocalDate horizonEndExclusive
    ) {
        Instant effectiveGeneratedAt = Objects.requireNonNull(
                generatedAt,
                "생성 시각은 필수입니다."
        );
        LocalDate effectiveHorizonEnd = Objects.requireNonNull(
                horizonEndExclusive,
                "생성 종료일은 필수입니다."
        );
        Chore chore = lockedActiveChore(choreId);
        LocalDate generatedOn = effectiveGeneratedAt
                .atZone(chore.getGroup().timeZone())
                .toLocalDate();
        if (!effectiveHorizonEnd.isAfter(generatedOn)) {
            throw new IllegalArgumentException("생성 종료일은 생성 기준일보다 뒤여야 합니다.");
        }

        List<ChoreOccurrence> ensured = new ArrayList<>();
        OccurrenceSchedule schedule = scheduleResolver.resolve(chore, effectiveGeneratedAt);
        while (schedule.periodStart().isBefore(effectiveHorizonEnd)) {
            ensured.add(ensureOccurrence(
                    chore,
                    schedule,
                    effectiveGeneratedAt
            ));
            Instant nextPeriodReference = schedule.periodEndExclusive()
                    .atStartOfDay(chore.getGroup().timeZone())
                    .toInstant();
            schedule = scheduleResolver.resolve(chore, nextPeriodReference);
        }
        return List.copyOf(ensured);
    }

    private Chore lockedActiveChore(Long choreId) {
        Objects.requireNonNull(choreId, "업무 ID는 필수입니다.");
        Long groupId = choreRepository.findGroupIdById(choreId)
                .orElseThrow(() -> new ChoreNotFoundException(choreId));
        sharingGroupRepository.findByIdForUpdate(groupId)
                .orElseThrow(() -> new IllegalStateException("업무의 그룹을 찾을 수 없습니다."));
        Chore chore = choreRepository.findByIdForUpdate(choreId)
                .orElseThrow(() -> new ChoreNotFoundException(choreId));
        if (!chore.isActive()) {
            throw new IllegalStateException("비활성 업무의 새 회차는 생성할 수 없습니다.");
        }
        return chore;
    }

    private ChoreOccurrence ensureOccurrence(
            Chore chore,
            OccurrenceSchedule schedule,
            Instant generatedAt
    ) {
        var exactOccurrence = occurrenceRepository
                .findByChore_IdAndScheduleRevisionSnapshotAndPeriodStartAndStatusNot(
                        chore.getId(),
                        chore.getScheduleRevision(),
                        schedule.periodStart(),
                        gdg.sharinglog.domain.rotation.OccurrenceStatus.CANCELLED
                );
        if (exactOccurrence.isPresent()) {
            return exactOccurrence.get();
        }

        var overlappingOccurrence = occurrenceRepository
                .findAllNonCancelledOverlapping(
                        chore.getId(),
                        schedule.periodStart(),
                        schedule.periodEndExclusive(),
                        PageRequest.of(0, 1)
                )
                .stream()
                .findFirst();
        if (overlappingOccurrence.isPresent()) {
            return overlappingOccurrence.get();
        }
        return createAndAssign(chore, schedule, generatedAt);
    }

    @Transactional
    public Optional<ChoreOccurrence> rescheduleActiveOccurrence(
            Chore chore,
            Instant referenceInstant
    ) {
        Chore requiredChore = Objects.requireNonNull(chore, "업무는 필수입니다.");
        Instant effectiveReference =
                Objects.requireNonNull(referenceInstant, "기준 시각은 필수입니다.");
        var activeOn = effectiveReference.atZone(requiredChore.getGroup().timeZone()).toLocalDate();
        List<ChoreOccurrence> activeOccurrences = occurrenceRepository
                .findAllOpenActiveOnByChoreIdForUpdate(requiredChore.getId(), activeOn);
        if (activeOccurrences.isEmpty()) {
            return Optional.empty();
        }
        if (activeOccurrences.size() > 1) {
            throw new IllegalStateException("같은 업무에 활성 미종료 회차가 둘 이상 존재합니다.");
        }

        OccurrenceSchedule schedule = scheduleResolver.resolve(requiredChore, effectiveReference);
        ChoreOccurrence occurrence = activeOccurrences.getFirst();
        occurrence.rescheduleToCurrentRevision(
                schedule.periodStart(),
                schedule.periodEndExclusive(),
                schedule.dueAt()
        );
        return Optional.of(occurrenceRepository.saveAndFlush(occurrence));
    }

    private ChoreOccurrence createAndAssign(
            Chore chore,
            OccurrenceSchedule schedule,
            Instant createdAt
    ) {
        ChoreOccurrence occurrence = occurrenceRepository.saveAndFlush(
                ChoreOccurrence.create(
                        chore,
                        schedule.periodStart(),
                        schedule.periodEndExclusive(),
                        schedule.dueAt(),
                        createdAt
                )
        );

        enrollmentService.synchronizeAutomaticEnrollments(chore, createdAt);
        List<GroupMember> eligibleMembers = enrollmentService.findActiveMembers(chore);
        List<OccurrenceEligibleMember> snapshots = eligibleMembers.stream()
                .map(member -> new OccurrenceEligibleMember(
                        occurrence,
                        occurrence.getEligibilitySnapshotVersion(),
                        member,
                        createdAt
                ))
                .toList();
        occurrenceEligibleMemberRepository.saveAllAndFlush(snapshots);
        assignmentService.assign(occurrence.getId(), AssignmentTrigger.INITIAL, createdAt);
        return occurrence;
    }

}
