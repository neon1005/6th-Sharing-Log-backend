package gdg.sharinglog.service.rotation.assignment;

import java.util.List;
import java.util.stream.Collectors;

import gdg.sharinglog.rotation.engine.CandidateSnapshot;
import gdg.sharinglog.rotation.engine.SelectionReason;

final class CandidateAuditFormatter {

    private CandidateAuditFormatter() {
    }

    static String snapshot(List<CandidateSnapshot> candidates) {
        return candidates.stream()
                .map(candidate -> (
                        "%d|active=%s|eligible=%s|declined=%s"
                                + "|sameChoreActualValidAssignments=%d"
                                + "|fairnessCredit=%d"
                                + "|sameChoreEffectiveValidAssignments=%d"
                                + "|sameFrequencyValidAssignments=%d"
                                + "|activePeriodLoad=%d"
                                + "|previous=%s|decision=%s"
                )
                        .formatted(
                                candidate.membershipId(),
                                candidate.active(),
                                candidate.eligible(),
                                candidate.declinedCurrentOccurrence(),
                                candidate.validSameChoreAssignmentCount(),
                                candidate.fairnessCredit(),
                                candidate.effectiveValidSameChoreAssignmentCount(),
                                candidate.validSameFrequencyAssignmentCount(),
                                candidate.activePeriodLoad(),
                                candidate.previousAssignee(),
                                candidate.decision()
                        ))
                .collect(Collectors.joining("\n"));
    }

    static String summary(List<SelectionReason> reasons) {
        return reasons.stream()
                .map(reason -> reason.code().name())
                .collect(Collectors.joining(" > "));
    }
}
