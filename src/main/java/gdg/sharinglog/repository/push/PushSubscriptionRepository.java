package gdg.sharinglog.repository.push;

import java.util.List;
import java.util.Optional;

import gdg.sharinglog.domain.push.PushSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {

    Optional<PushSubscription> findByEndpoint(String endpoint);

    List<PushSubscription> findAllByUser_Id(Long userId);

    void deleteByEndpointAndUser_Id(String endpoint, Long userId);
}
