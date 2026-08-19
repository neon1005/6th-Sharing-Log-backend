package gdg.sharinglog.service.rotation.api.chore;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import gdg.sharinglog.domain.GroupMember;
import gdg.sharinglog.domain.MemberStatus;
import gdg.sharinglog.domain.rotation.Chore;
import gdg.sharinglog.domain.rotation.ChoreEligibilityMode;
import gdg.sharinglog.domain.rotation.ChoreFrequency;
import gdg.sharinglog.repository.GroupMemberRepository;
import gdg.sharinglog.repository.rotation.ChoreRepository;
import gdg.sharinglog.service.rotation.enrollment.ChoreEnrollmentService;
import gdg.sharinglog.service.rotation.occurrence.OccurrenceGenerationService;
import gdg.sharinglog.service.rotation.occurrence.OccurrencePlanService;
import gdg.sharinglog.service.rotation.access.RotationActor;
import gdg.sharinglog.service.rotation.access.RotationActorAccessService;
import gdg.sharinglog.service.rotation.access.RotationMemberNotFoundException;
import gdg.sharinglog.web.rotation.error.RotationConflictException;
import gdg.sharinglog.web.rotation.error.RotationNotFoundException;
import gdg.sharinglog.web.rotation.error.RotationProblemCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChoreApplicationService {

    private final RotationActorAccessService accessService;
    private final GroupMemberRepository groupMemberRepository;
    private final ChoreRepository choreRepository;
    private final ChoreEnrollmentService enrollmentService;
    private final OccurrenceGenerationService occurrenceGenerationService;
    private final OccurrencePlanService occurrencePlanService;

    @Transactional
    public CreatedChore create(
            String groupPublicId,
            String registrationId,
            OAuth2User principal,
            CreateChoreCommand command,
            Instant createdAt
    ) {
        Objects.requireNonNull(command, "업무 생성 명령은 필수입니다.");
        Instant effectiveCreatedAt =
                Objects.requireNonNull(createdAt, "업무 생성 시각은 필수입니다.");
        RotationActor actor =
                accessService.requireOwnerForUpdate(groupPublicId, registrationId, principal);
        List<GroupMember> selectedMembers = resolveSelectedMembers(actor, command);
        Chore chore = choreRepository.saveAndFlush(createChore(actor, command, effectiveCreatedAt));
        enrollmentService.initializeChore(chore, selectedMembers, effectiveCreatedAt);

        var occurrence = occurrencePlanService
                .ensureRollingHorizon(chore, effectiveCreatedAt)
                .getFirst();
        return new CreatedChore(new ChoreView(chore, selectedMembers), occurrence, actor);
    }

    @Transactional(readOnly = true)
    public List<ChoreView> findAll(
            String groupPublicId,
            String registrationId,
            OAuth2User principal,
            ChoreFrequency frequency,
            Boolean active
    ) {
        RotationActor actor =
                accessService.requireActiveMember(groupPublicId, registrationId, principal);
        return choreRepository.findAllByGroup_IdOrderById(actor.group().getId()).stream()
                .filter(chore -> frequency == null || chore.getFrequency() == frequency)
                .filter(chore -> active == null || chore.isActive() == active)
                .map(chore -> new ChoreView(chore, currentEligibleMembers(chore)))
                .toList();
    }

    @Transactional
    public ChoreView update(
            String groupPublicId,
            String chorePublicId,
            String registrationId,
            OAuth2User principal,
            UpdateChoreCommand command,
            long expectedVersion,
            Instant changedAt
    ) {
        Objects.requireNonNull(command, "업무 수정 명령은 필수입니다.");
        Instant effectiveChangedAt =
                Objects.requireNonNull(changedAt, "업무 수정 시각은 필수입니다.");
        RotationActor actor =
                accessService.requireOwnerForUpdate(groupPublicId, registrationId, principal);
        Chore chore = choreRepository
                .findByPublicIdAndGroupPublicIdForUpdate(chorePublicId, groupPublicId)
                .orElseThrow(() -> new RotationNotFoundException(
                        "The requested chore was not found."
                ));
        requireVersion(chore, expectedVersion);
        if (!chore.getGroup().getId().equals(actor.group().getId())) {
            throw new IllegalStateException("잠긴 업무와 접근 그룹이 일치하지 않습니다.");
        }

        String originalName = chore.getName();
        if (command.name() != null) {
            chore.rename(command.name());
        }
        boolean nameChanged = !originalName.equals(chore.getName());
        boolean scheduleChanged = false;
        if (command.schedule() != null) {
            UpdateChoreCommand.Schedule schedule = command.schedule();
            scheduleChanged = chore.reschedule(
                    schedule.frequency(),
                    schedule.dueTime(),
                    schedule.weeklyDueDay(),
                    schedule.biweeklyAnchorDate()
            );
        }
        boolean eligibilityChanged = command.eligibility() != null
                && updateEligibility(
                        actor,
                        chore,
                        command.eligibility(),
                        effectiveChangedAt
                );

        Chore updated = choreRepository.saveAndFlush(chore);
        if (scheduleChanged) {
            occurrenceGenerationService.rescheduleActiveOccurrence(
                    updated,
                    effectiveChangedAt
            );
            occurrencePlanService.regenerateFutureAfterScheduleChange(
                    updated,
                    effectiveChangedAt
            );
        } else if (nameChanged || eligibilityChanged) {
            occurrencePlanService.regenerateFuture(updated, effectiveChangedAt);
        }
        return new ChoreView(updated, currentEligibleMembers(updated));
    }

    @Transactional
    public long deactivate(
            String groupPublicId,
            String chorePublicId,
            String registrationId,
            OAuth2User principal,
            long expectedVersion,
            Instant changedAt
    ) {
        Instant effectiveChangedAt = Objects.requireNonNull(
                changedAt,
                "Chore deactivation time is required."
        );
        accessService.requireOwnerForUpdate(groupPublicId, registrationId, principal);
        Chore chore = choreRepository
                .findByPublicIdAndGroupPublicIdForUpdate(chorePublicId, groupPublicId)
                .orElseThrow(() -> new RotationNotFoundException(
                        "The requested chore was not found."
                ));
        requireVersion(chore, expectedVersion);
        if (chore.isActive()) {
            chore.deactivate();
            choreRepository.saveAndFlush(chore);
            occurrencePlanService.cancelFutureForDeactivation(chore, effectiveChangedAt);
        }
        return chore.getVersion();
    }

    private Chore createChore(
            RotationActor actor,
            CreateChoreCommand command,
            Instant createdAt
    ) {
        return switch (Objects.requireNonNull(command.frequency(), "반복 주기는 필수입니다.")) {
            case DAILY -> Chore.daily(
                    actor.group(),
                    actor.membership(),
                    command.name(),
                    command.eligibilityMode(),
                    command.dueTime(),
                    createdAt
            );
            case WEEKLY -> Chore.weekly(
                    actor.group(),
                    actor.membership(),
                    command.name(),
                    command.eligibilityMode(),
                    command.weeklyDueDay(),
                    command.dueTime(),
                    createdAt
            );
            case BIWEEKLY -> Chore.biweekly(
                    actor.group(),
                    actor.membership(),
                    command.name(),
                    command.eligibilityMode(),
                    command.biweeklyAnchorDate(),
                    command.dueTime(),
                    createdAt
            );
        };
    }

    private List<GroupMember> resolveSelectedMembers(
            RotationActor actor,
            CreateChoreCommand command
    ) {
        return resolveSelectedMembers(
                actor,
                command.eligibilityMode(),
                command.eligibleMembershipPublicIds()
        );
    }

    private List<GroupMember> resolveSelectedMembers(
            RotationActor actor,
            ChoreEligibilityMode mode,
            List<String> requested
    ) {
        ChoreEligibilityMode requiredMode =
                Objects.requireNonNull(mode, "가능 멤버 모드는 필수입니다.");
        List<String> requiredRequested =
                Objects.requireNonNull(requested, "가능 멤버 목록은 필수입니다.");
        if (requiredMode == ChoreEligibilityMode.ALL_ACTIVE_MEMBERS) {
            if (!requiredRequested.isEmpty()) {
                throw new IllegalArgumentException("전체 멤버 모드에는 개별 멤버를 지정할 수 없습니다.");
            }
            return List.of();
        }

        Set<String> distinct = new HashSet<>(requiredRequested);
        if (requiredRequested.isEmpty() || distinct.size() != requiredRequested.size()) {
            throw new IllegalArgumentException("선택 멤버는 중복 없이 한 명 이상이어야 합니다.");
        }
        List<GroupMember> members =
                groupMemberRepository.findAllByGroup_IdAndPublicIdInAndStatusOrderById(
                        actor.group().getId(),
                        distinct,
                        MemberStatus.ACTIVE
                );
        Set<String> resolved = members.stream()
                .map(GroupMember::getPublicId)
                .collect(java.util.stream.Collectors.toSet());
        if (!resolved.equals(distinct)) {
            throw new RotationMemberNotFoundException(
                    distinct.stream().filter(id -> !resolved.contains(id)).findFirst().orElse("")
            );
        }
        return members;
    }

    private boolean updateEligibility(
            RotationActor actor,
            Chore chore,
            UpdateChoreCommand.Eligibility eligibility,
            Instant changedAt
    ) {
        ChoreEligibilityMode mode =
                Objects.requireNonNull(eligibility.mode(), "가능 멤버 모드는 필수입니다.");
        List<GroupMember> selectedMembers = resolveSelectedMembers(
                actor,
                mode,
                eligibility.eligibleMembershipPublicIds()
        );
        List<GroupMember> desiredMembers = mode == ChoreEligibilityMode.ALL_ACTIVE_MEMBERS
                ? groupMemberRepository.findAllByGroup_IdAndStatusOrderById(
                        actor.group().getId(),
                        MemberStatus.ACTIVE
                )
                : selectedMembers;
        List<GroupMember> currentMembers = currentEligibleMembers(chore);
        Set<String> desiredIds = desiredMembers.stream()
                .map(GroupMember::getPublicId)
                .collect(java.util.stream.Collectors.toSet());
        Set<String> currentIds = currentMembers.stream()
                .map(GroupMember::getPublicId)
                .collect(java.util.stream.Collectors.toSet());

        boolean changed = chore.changeEligibilityMode(mode);
        for (GroupMember current : currentMembers) {
            if (!desiredIds.contains(current.getPublicId())) {
                changed |= enrollmentService.removeOrDisable(chore, current, changedAt);
            }
        }
        for (GroupMember desired : desiredMembers) {
            if (!currentIds.contains(desired.getPublicId())) {
                changed |= enrollmentService.addOrReactivate(chore, desired, changedAt);
            }
        }
        return changed;
    }

    private List<GroupMember> currentEligibleMembers(Chore chore) {
        return enrollmentService.findActiveMembers(chore);
    }

    private void requireVersion(Chore chore, long expectedVersion) {
        if (chore.getVersion() != expectedVersion) {
            throw new RotationConflictException(
                    RotationProblemCode.VERSION_CONFLICT,
                    "The chore changed. Reload it and try again.",
                    Map.of(
                            "resourceId", chore.getPublicId(),
                            "expectedVersion", expectedVersion,
                            "currentVersion", chore.getVersion()
                    )
            );
        }
    }
}
