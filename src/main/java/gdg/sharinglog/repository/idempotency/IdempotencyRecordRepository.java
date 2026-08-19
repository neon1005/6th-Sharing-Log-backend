package gdg.sharinglog.repository.idempotency;

import java.time.Instant;
import java.util.Optional;

import gdg.sharinglog.domain.idempotency.IdempotencyHttpMethod;
import gdg.sharinglog.domain.idempotency.IdempotencyRecord;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select record
            from IdempotencyRecord record
            where record.actor.id = :actorUserId
              and record.httpMethod = :httpMethod
              and record.uriHash = :uriHash
              and record.idempotencyKey = :idempotencyKey
            """)
    Optional<IdempotencyRecord> findByIdentityForUpdate(
            @Param("actorUserId") Long actorUserId,
            @Param("httpMethod") IdempotencyHttpMethod httpMethod,
            @Param("uriHash") String uriHash,
            @Param("idempotencyKey") String idempotencyKey
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from IdempotencyRecord record where record.expiresAt <= :now")
    int deleteExpiredAtOrBefore(@Param("now") Instant now);
}
