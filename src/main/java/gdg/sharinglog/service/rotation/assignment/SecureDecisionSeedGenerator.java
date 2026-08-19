package gdg.sharinglog.service.rotation.assignment;

import java.security.SecureRandom;

import org.springframework.stereotype.Component;

@Component
public class SecureDecisionSeedGenerator implements DecisionSeedGenerator {

    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public long nextSeed() {
        return secureRandom.nextLong();
    }
}
