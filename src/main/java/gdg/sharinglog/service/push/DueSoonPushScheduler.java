package gdg.sharinglog.service.push;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.function.BiConsumer;

import gdg.sharinglog.domain.User;
import gdg.sharinglog.domain.rotation.ChoreOccurrence;
import gdg.sharinglog.repository.rotation.ChoreOccurrenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class DueSoonPushScheduler {

    private final ChoreOccurrenceRepository occurrenceRepository;
    private final PushNotifier pushNotifier;

    @Scheduled(
            cron = "${sharing-log.push.due-soon-scheduler.cron:0 */5 * * * *}",
            zone = "UTC"
    )
    @Transactional
    public void sendDueSoonReminders() {
        Instant now = Instant.now();
        sendReminders(
                occurrenceRepository.findAllNeedingDueSoon24hNotification(now, now.plus(24, ChronoUnit.HOURS)),
                "24시간",
                now,
                ChoreOccurrence::markDueSoon24hNotified
        );
        sendReminders(
                occurrenceRepository.findAllNeedingDueSoon3hNotification(now, now.plus(3, ChronoUnit.HOURS)),
                "3시간",
                now,
                ChoreOccurrence::markDueSoon3hNotified
        );
    }

    private void sendReminders(
            List<ChoreOccurrence> occurrences,
            String remainingLabel,
            Instant now,
            BiConsumer<ChoreOccurrence, Instant> markNotified
    ) {
        for (ChoreOccurrence occurrence : occurrences) {
            try {
                occurrence.currentAssignee().ifPresent(assignee -> {
                    User user = assignee.getUser();
                    if (user.isDueSoonPushEnabled()) {
                        pushNotifier.notifyUser(
                                user.getId(),
                                "마감 임박",
                                "'" + occurrence.getChoreNameSnapshot() + "' 마감이 " + remainingLabel + " 남았어요",
                                "/notification"
                        );
                    }
                });
                markNotified.accept(occurrence, now);
            } catch (RuntimeException exception) {
                log.error(
                        "Failed to send a due-soon push for occurrence {}",
                        occurrence.getPublicId(),
                        exception
                );
            }
        }
    }
}
