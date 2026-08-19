package gdg.sharinglog.repository.rotation;

import java.util.List;
import java.util.Optional;

import gdg.sharinglog.domain.rotation.SubstituteRecipientStatus;
import gdg.sharinglog.domain.rotation.SubstituteRequestRecipient;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubstituteRequestRecipientRepository
        extends JpaRepository<SubstituteRequestRecipient, Long> {

    @EntityGraph(attributePaths = {"member", "member.user"})
    List<SubstituteRequestRecipient> findAllByRequest_IdOrderById(Long requestId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"member", "member.user"})
    @Query("""
            select recipient
            from SubstituteRequestRecipient recipient
            where recipient.request.id = :requestId
              and recipient.member.id = :membershipId
            """)
    Optional<SubstituteRequestRecipient> findForUpdate(
            @Param("requestId") Long requestId,
            @Param("membershipId") Long membershipId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"request", "request.occurrence"})
    List<SubstituteRequestRecipient>
    findAllByMember_IdAndResponseStatus(
            Long membershipId,
            SubstituteRecipientStatus responseStatus
    );
}
