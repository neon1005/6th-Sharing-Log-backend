package gdg.sharinglog.web.rotation.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import gdg.sharinglog.domain.rotation.ChoreEligibilityMode;
import org.junit.jupiter.api.Test;

class UpdateChoreRequestTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void deserializesEligibilityInsteadOfIgnoringIt() throws Exception {
        String firstId = "44444444-4444-4444-8444-444444444444";
        String secondId = "55555555-5555-4555-8555-555555555555";

        UpdateChoreRequest request = objectMapper.readValue("""
                {
                  "eligibility": {
                    "mode": "SELECTED_MEMBERS",
                    "membershipIds": [
                      "44444444-4444-4444-8444-444444444444",
                      "55555555-5555-4555-8555-555555555555"
                    ]
                  }
                }
                """, UpdateChoreRequest.class);

        assertTrue(request.isChangePresent());
        assertEquals(ChoreEligibilityMode.SELECTED_MEMBERS, request.eligibility().mode());
        assertEquals(List.of(firstId, secondId), request.eligibility().membershipIds());
    }
}
