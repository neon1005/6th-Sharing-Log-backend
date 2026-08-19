package gdg.sharinglog.service.rotation.enrollment;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import gdg.sharinglog.domain.GroupMember;
import gdg.sharinglog.domain.MemberStatus;
import gdg.sharinglog.domain.rotation.Chore;
import gdg.sharinglog.domain.rotation.ChoreEligibleMember;
import gdg.sharinglog.domain.rotation.ChoreEligibilityMode;
import gdg.sharinglog.repository.GroupMemberRepository;
import gdg.sharinglog.repository.rotation.ChoreAssignmentAttemptRepository;
import gdg.sharinglog.repository.rotation.ChoreEligibleMemberRepository;
import gdg.sharinglog.repository.rotation.ChoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChoreEnrollmentService {

    private final GroupMemberRepository groupMemberRepository;
    private final ChoreRepository choreRepository;
    private final ChoreEligibleMemberRepository enrollmentRepository;
    private final ChoreAssignmentAttemptRepository assignmentRepository;

    @Transactional
    public void initializeChore(
            Chore chore,
            List<GroupMember> selectedMembers,
            Instant enrolledAt
    ) {
        Chore requiredChore = requirePersistedChore(chore);
        Instant effectiveEnrolledAt =
                Objects.requireNonNull(enrolledAt, "로테이션 등록 시각은 필수입니다.");
        List<GroupMember> initialMembers =
                requiredChore.getEligibilityMode() == ChoreEligibilityMode.ALL_ACTIVE_MEMBERS
                        ? groupMemberRepository.findAllByGroup_IdAndStatusOrderById(
                                requiredChore.getGroup().getId(),
                                MemberStatus.ACTIVE
                        )
                        : List.copyOf(Objects.requireNonNull(
                                selectedMembers,
                                "선택 멤버 목록은 필수입니다."
                        ));

        List<ChoreEligibleMember> created = initialMembers.stream()
                .map(member -> {
                    requireActiveMemberOfChore(requiredChore, member);
                    return enrollmentRepository
                            .findByChore_IdAndMember_Id(requiredChore.getId(), member.getId())
                            .orElseGet(() -> new ChoreEligibleMember(
                                    requiredChore,
                                    member,
                                    effectiveEnrolledAt,
                                    0L
                            ));
                })
                .filter(enrollment -> enrollment.getId() == null)
                .toList();
        if (created.isEmpty()) {
            return;
        }

        enrollmentRepository.saveAllAndFlush(created);
        touchAndFlush(requiredChore);
    }

    @Transactional
    public void synchronizeAutomaticEnrollments(Chore chore, Instant enrolledAt) {
        Chore requiredChore = requirePersistedChore(chore);
        if (requiredChore.getEligibilityMode() != ChoreEligibilityMode.ALL_ACTIVE_MEMBERS) {
            return;
        }
        Instant effectiveEnrolledAt =
                Objects.requireNonNull(enrolledAt, "로테이션 등록 시각은 필수입니다.");
        List<ChoreEligibleMember> current =
                enrollmentRepository.findAllByChore_IdOrderById(requiredChore.getId());
        List<GroupMember> activeMembers =
                groupMemberRepository.findAllByGroup_IdAndStatusOrderById(
                        requiredChore.getGroup().getId(),
                        MemberStatus.ACTIVE
                );

        if (current.isEmpty()) {
            initializeChore(requiredChore, activeMembers, effectiveEnrolledAt);
            return;
        }

        Map<Long, ChoreEligibleMember> byMemberId = new HashMap<>();
        current.forEach(enrollment -> byMemberId.put(enrollment.getMember().getId(), enrollment));
        for (GroupMember member : activeMembers) {
            ChoreEligibleMember enrollment = byMemberId.get(member.getId());
            if (enrollment == null
                    || (enrollment.isEnabled() && !enrollment.belongsToCurrentActivation())) {
                addOrReactivate(requiredChore, member, effectiveEnrolledAt);
            }
        }
    }

    @Transactional
    public void activateMemberEnrollments(GroupMember member, Instant activatedAt) {
        GroupMember requiredMember = requirePersistedActiveMember(member);
        Instant effectiveActivatedAt =
                Objects.requireNonNull(activatedAt, "멤버 활성 시각은 필수입니다.");
        Map<Long, ChoreEligibleMember> existingByChoreId = new HashMap<>();
        enrollmentRepository.findAllByMember_IdOrderByChore_Id(requiredMember.getId())
                .forEach(enrollment ->
                        existingByChoreId.put(enrollment.getChore().getId(), enrollment));

        List<Chore> activeChores =
                choreRepository.findAllByGroup_IdAndActiveTrueOrderById(
                        requiredMember.getGroup().getId()
                );
        for (Chore chore : activeChores) {
            ChoreEligibleMember enrollment = existingByChoreId.get(chore.getId());
            if (enrollment == null) {
                if (chore.getEligibilityMode() == ChoreEligibilityMode.ALL_ACTIVE_MEMBERS) {
                    addOrReactivate(chore, requiredMember, effectiveActivatedAt);
                }
                continue;
            }
            if (enrollment.isEnabled() && !enrollment.belongsToCurrentActivation()) {
                addOrReactivate(chore, requiredMember, effectiveActivatedAt);
            }
        }
    }

    @Transactional
    public boolean addOrReactivate(
            Chore chore,
            GroupMember member,
            Instant enrolledAt
    ) {
        Chore requiredChore = requirePersistedChore(chore);
        GroupMember requiredMember = requirePersistedActiveMember(member);
        requireActiveMemberOfChore(requiredChore, requiredMember);
        Instant effectiveEnrolledAt =
                Objects.requireNonNull(enrolledAt, "로테이션 등록 시각은 필수입니다.");

        ChoreEligibleMember enrollment = enrollmentRepository
                .findByChore_IdAndMember_Id(requiredChore.getId(), requiredMember.getId())
                .orElse(null);
        if (enrollment != null
                && enrollment.isEnabled()
                && enrollment.belongsToCurrentActivation()) {
            return false;
        }

        long fairnessCredit = tailFairnessCredit(
                requiredChore,
                requiredMember,
                enrollment == null ? 0L : enrollment.getFairnessCredit(),
                effectiveEnrolledAt
        );
        if (enrollment == null) {
            enrollment = new ChoreEligibleMember(
                    requiredChore,
                    requiredMember,
                    effectiveEnrolledAt,
                    fairnessCredit
            );
        } else {
            enrollment.enableAtBack(effectiveEnrolledAt, fairnessCredit);
        }
        enrollmentRepository.saveAndFlush(enrollment);
        touchAndFlush(requiredChore);
        return true;
    }

    @Transactional
    public boolean removeOrDisable(
            Chore chore,
            GroupMember member,
            Instant disabledAt
    ) {
        Chore requiredChore = requirePersistedChore(chore);
        GroupMember requiredMember =
                Objects.requireNonNull(member, "로테이션 멤버는 필수입니다.");
        Instant effectiveDisabledAt =
                Objects.requireNonNull(disabledAt, "로테이션 제외 시각은 필수입니다.");
        ChoreEligibleMember enrollment = enrollmentRepository
                .findByChore_IdAndMember_Id(requiredChore.getId(), requiredMember.getId())
                .orElse(null);
        if (enrollment == null || !enrollment.isEnabled()) {
            return false;
        }

        enrollment.disable(effectiveDisabledAt);
        enrollmentRepository.saveAndFlush(enrollment);
        touchAndFlush(requiredChore);
        return true;
    }

    @Transactional(readOnly = true)
    public List<GroupMember> findActiveMembers(Chore chore) {
        Chore requiredChore = requirePersistedChore(chore);
        return enrollmentRepository
                .findAllByChore_IdAndEnabledTrueOrderById(requiredChore.getId())
                .stream()
                .filter(ChoreEligibleMember::belongsToCurrentActivation)
                .map(ChoreEligibleMember::getMember)
                .filter(GroupMember::isActive)
                .toList();
    }

    private long tailFairnessCredit(
            Chore chore,
            GroupMember joiningMember,
            long existingCredit,
            Instant enrolledAt
    ) {
        LocalDate enrolledOn = enrolledAt
                .atZone(chore.getGroup().timeZone())
                .toLocalDate();
        long maximumEffectiveAssignments = -1L;
        for (ChoreEligibleMember enrollment :
                enrollmentRepository.findAllByChore_IdAndEnabledTrueOrderById(chore.getId())) {
            GroupMember enrolledMember = enrollment.getMember();
            if (enrolledMember.getId().equals(joiningMember.getId())
                    || !enrolledMember.isActive()
                    || !enrollment.belongsToCurrentActivation()) {
                continue;
            }
            long actualAssignments = assignmentRepository
                    .countValidAssignmentsForChoreAndMemberThroughPeriod(
                            chore.getId(),
                            enrolledOn,
                            enrolledMember.getId()
                    );
            maximumEffectiveAssignments = Math.max(
                    maximumEffectiveAssignments,
                    enrollment.effectiveAssignmentCount(actualAssignments)
            );
        }
        if (maximumEffectiveAssignments < 0) {
            return existingCredit;
        }

        long joiningActualAssignments = assignmentRepository
                .countValidAssignmentsForChoreAndMemberThroughPeriod(
                        chore.getId(),
                        enrolledOn,
                        joiningMember.getId()
                );
        long nextTailCount = Math.incrementExact(maximumEffectiveAssignments);
        long requiredCredit = nextTailCount > joiningActualAssignments
                ? Math.subtractExact(nextTailCount, joiningActualAssignments)
                : 0L;
        return Math.max(existingCredit, requiredCredit);
    }

    private Chore requirePersistedChore(Chore chore) {
        Chore required = Objects.requireNonNull(chore, "업무는 필수입니다.");
        if (required.getId() == null) {
            throw new IllegalArgumentException("저장된 업무만 로테이션을 변경할 수 있습니다.");
        }
        return required;
    }

    private GroupMember requirePersistedActiveMember(GroupMember member) {
        GroupMember required = Objects.requireNonNull(member, "로테이션 멤버는 필수입니다.");
        if (required.getId() == null) {
            throw new IllegalArgumentException("저장된 멤버만 로테이션에 등록할 수 있습니다.");
        }
        if (!required.isActive()) {
            throw new IllegalArgumentException("활성 멤버만 로테이션에 등록할 수 있습니다.");
        }
        return required;
    }

    private void requireActiveMemberOfChore(Chore chore, GroupMember member) {
        if (!member.isActive()) {
            throw new IllegalArgumentException("활성 멤버만 로테이션에 등록할 수 있습니다.");
        }
        if (!member.getGroup().getId().equals(chore.getGroup().getId())) {
            throw new IllegalArgumentException("멤버와 업무는 같은 그룹에 속해야 합니다.");
        }
    }

    private void touchAndFlush(Chore chore) {
        chore.recordEnrollmentChange();
        choreRepository.saveAndFlush(chore);
    }
}
