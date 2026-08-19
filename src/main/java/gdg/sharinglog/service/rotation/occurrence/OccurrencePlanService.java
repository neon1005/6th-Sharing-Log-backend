package gdg.sharinglog.service.rotation.occurrence;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

import gdg.sharinglog.domain.rotation.Chore;
import gdg.sharinglog.domain.rotation.ChoreOccurrence;
import gdg.sharinglog.repository.SharingGroupRepository;
import gdg.sharinglog.repository.rotation.ChoreOccurrenceRepository;
import gdg.sharinglog.repository.rotation.ChoreRepository;
import gdg.sharinglog.service.rotation.substitute.SubstituteRequestLifecycleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OccurrencePlanService {

    private final SharingGroupRepository groupRepository;
    private final ChoreRepository choreRepository;
    private final ChoreOccurrenceRepository occurrenceRepository;
    private final SubstituteRequestLifecycleService substituteRequestLifecycleService;
    private final OccurrenceGenerationService generationService;

    @Transactional
    public List<ChoreOccurrence> ensureRollingHorizon(Chore chore, Instant generatedAt) {
        Chore requiredChore = requirePersistedChore(chore);
        Instant effectiveGeneratedAt = Objects.requireNonNull(
                generatedAt,
                "Plan generation time is required."
        );
        LocalDate activeOn = effectiveGeneratedAt
                .atZone(requiredChore.getGroup().timeZone())
                .toLocalDate();
        LocalDate horizonEnd = OccurrencePlanningHorizon.horizonEndExclusive(
                requiredChore.getGroup(),
                activeOn
        );
        return generationService.ensureOccurrencesUntil(
                requiredChore.getId(),
                effectiveGeneratedAt,
                horizonEnd
        );
    }

    @Transactional
    public void regenerateFuture(Chore chore, Instant changedAt) {
        rebuildFuture(List.of(requirePersistedChore(chore)), changedAt, true, true);
    }

    @Transactional
    public void regenerateFutureAfterScheduleChange(Chore chore, Instant changedAt) {
        rebuildFuture(List.of(requirePersistedChore(chore)), changedAt, false, true);
    }

    @Transactional
    public void regenerateFutures(Collection<Chore> chores, Instant changedAt) {
        rebuildFuture(orderedDistinct(chores), changedAt, true, true);
    }

    @Transactional
    public void regenerateGroupFuture(Long groupId, Instant changedAt) {
        Long requiredGroupId = Objects.requireNonNull(groupId, "Group ID is required.");
        groupRepository.findByIdForUpdate(requiredGroupId)
                .orElseThrow(() -> new IllegalStateException("The rotation group was not found."));
        rebuildFuture(
                choreRepository.findAllActiveByGroupIdForUpdate(requiredGroupId),
                changedAt,
                true,
                true
        );
    }

    @Transactional
    public void cancelFutureForDeactivation(Chore chore, Instant changedAt) {
        Chore requiredChore = requirePersistedChore(chore);
        Instant effectiveChangedAt = Objects.requireNonNull(
                changedAt,
                "Plan change time is required."
        );
        cancelOccurrences(
                occurrenceRepository.findAllOpenByChoreIdForUpdate(requiredChore.getId()),
                effectiveChangedAt
        );
        occurrenceRepository.flush();
        requiredChore.advancePlanningRevision();
        choreRepository.saveAndFlush(requiredChore);
    }

    @Transactional
    public void cancelGroupFuture(Long groupId, Instant changedAt) {
        Long requiredGroupId = Objects.requireNonNull(groupId, "Group ID is required.");
        groupRepository.findByIdForUpdate(requiredGroupId)
                .orElseThrow(() -> new IllegalStateException("The rotation group was not found."));
        rebuildFuture(
                choreRepository.findAllActiveByGroupIdForUpdate(requiredGroupId),
                changedAt,
                true,
                false
        );
    }

    private void rebuildFuture(
            List<Chore> chores,
            Instant changedAt,
            boolean advancePlanningRevision,
            boolean generateReplacement
    ) {
        Instant effectiveChangedAt = Objects.requireNonNull(
                changedAt,
                "Plan change time is required."
        );
        if (chores.isEmpty()) {
            return;
        }

        for (Chore chore : chores) {
            cancelFutureOccurrences(chore, effectiveChangedAt);
        }
        occurrenceRepository.flush();

        if (advancePlanningRevision) {
            for (Chore chore : chores) {
                chore.advancePlanningRevision();
            }
            choreRepository.saveAllAndFlush(chores);
        }

        if (generateReplacement) {
            for (Chore chore : chores) {
                if (chore.isActive()) {
                    ensureRollingHorizon(chore, effectiveChangedAt);
                }
            }
        }
    }

    private void cancelFutureOccurrences(Chore chore, Instant changedAt) {
        LocalDate activeOn = changedAt.atZone(chore.getGroup().timeZone()).toLocalDate();
        List<ChoreOccurrence> futureOccurrences = occurrenceRepository
                .findAllFutureOpenByChoreIdForUpdate(chore.getId(), activeOn);
        cancelOccurrences(futureOccurrences, changedAt);
    }

    private void cancelOccurrences(List<ChoreOccurrence> occurrences, Instant changedAt) {
        for (ChoreOccurrence occurrence : occurrences) {
            substituteRequestLifecycleService.cancelPendingForOccurrence(
                    occurrence,
                    changedAt
            );
            occurrence.cancelForPlanRegeneration(changedAt);
            occurrenceRepository.save(occurrence);
        }
    }

    private List<Chore> orderedDistinct(Collection<Chore> chores) {
        Objects.requireNonNull(chores, "Chores are required.");
        LinkedHashMap<Long, Chore> byId = new LinkedHashMap<>();
        chores.stream()
                .map(this::requirePersistedChore)
                .sorted(Comparator.comparingLong(Chore::getId))
                .forEach(chore -> byId.put(chore.getId(), chore));
        return List.copyOf(byId.values());
    }

    private Chore requirePersistedChore(Chore chore) {
        Chore required = Objects.requireNonNull(chore, "Chore is required.");
        if (required.getId() == null) {
            throw new IllegalArgumentException("A persisted chore is required.");
        }
        return required;
    }
}
