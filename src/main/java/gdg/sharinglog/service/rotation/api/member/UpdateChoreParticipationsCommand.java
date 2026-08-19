package gdg.sharinglog.service.rotation.api.member;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record UpdateChoreParticipationsCommand(
        List<String> addChoreIds,
        List<String> removeChoreIds,
        ChoreParticipationApplicationScope applicationScope,
        Map<String, Long> expectedVersions
) {

    public UpdateChoreParticipationsCommand {
        addChoreIds = List.copyOf(Objects.requireNonNull(
                addChoreIds,
                "추가할 업무 ID 목록은 필수입니다."
        ));
        removeChoreIds = List.copyOf(Objects.requireNonNull(
                removeChoreIds,
                "제외할 업무 ID 목록은 필수입니다."
        ));
        applicationScope = Objects.requireNonNull(
                applicationScope,
                "변경 적용 범위는 필수입니다."
        );
        Map<String, Long> requiredExpectedVersions = Objects.requireNonNull(
                expectedVersions,
                "업무별 예상 버전은 필수입니다."
        );

        Set<String> addIds = validatedIds(addChoreIds, "추가할 업무");
        Set<String> removeIds = validatedIds(removeChoreIds, "제외할 업무");
        if (addIds.isEmpty() && removeIds.isEmpty()) {
            throw new IllegalArgumentException("추가하거나 제외할 업무가 하나 이상 필요합니다.");
        }
        Set<String> overlap = new HashSet<>(addIds);
        overlap.retainAll(removeIds);
        if (!overlap.isEmpty()) {
            throw new IllegalArgumentException("같은 업무를 동시에 추가하고 제외할 수 없습니다.");
        }

        Set<String> affectedIds = new HashSet<>(addIds);
        affectedIds.addAll(removeIds);
        if (!requiredExpectedVersions.keySet().equals(affectedIds)
                || requiredExpectedVersions.entrySet().stream().anyMatch(entry ->
                entry.getKey() == null
                        || entry.getValue() == null
                        || entry.getValue() < 0)) {
            throw new IllegalArgumentException(
                    "업무별 예상 버전은 변경 대상 업무와 정확히 일치하는 0 이상의 값이어야 합니다."
            );
        }
        expectedVersions = Map.copyOf(requiredExpectedVersions);
    }

    private static Set<String> validatedIds(List<String> ids, String label) {
        Set<String> distinct = new HashSet<>();
        for (String id : ids) {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException(label + " ID는 비어 있을 수 없습니다.");
            }
            if (!distinct.add(id)) {
                throw new IllegalArgumentException(label + " ID는 중복될 수 없습니다.");
            }
        }
        return distinct;
    }
}
