package gdg.sharinglog.service.rotation.assignment;

@FunctionalInterface
public interface DecisionSeedGenerator {

    long nextSeed();
}
