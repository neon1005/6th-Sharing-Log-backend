package gdg.sharinglog.web.rotation.dto;

import java.time.Instant;

import gdg.sharinglog.domain.rotation.NoCandidateReason;

public record AttentionResponse(
        NoCandidateReason reason,
        Instant since,
        Instant lastDecisionAt
) {
}
