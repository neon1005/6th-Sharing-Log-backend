package gdg.sharinglog.service.rotation.occurrence;

import java.time.Instant;

import gdg.sharinglog.repository.rotation.ChoreRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class RotationOccurrenceScheduler {

    private final ChoreRepository choreRepository;
    private final OccurrencePlanService planService;

    @EventListener(ApplicationReadyEvent.class)
    public void ensureRollingHorizonsOnStartup() {
        ensureCurrentOccurrences();
    }

    @Scheduled(
            cron = "${sharing-log.rotation.scheduler.cron:0 5 * * * *}",
            zone = "UTC"
    )
    public void ensureCurrentOccurrences() {
        Instant referenceTime = Instant.now();
        for (var chore : choreRepository.findAllActiveForOccurrenceGeneration()) {
            try {
                planService.ensureRollingHorizon(chore, referenceTime);
            } catch (RuntimeException exception) {
                log.error(
                        "Failed to generate the rolling occurrence horizon for chore {}",
                        chore.getPublicId(),
                        exception
                );
            }
        }
    }
}
