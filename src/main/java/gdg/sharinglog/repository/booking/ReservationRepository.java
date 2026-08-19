package gdg.sharinglog.repository.booking;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import gdg.sharinglog.domain.booking.Reservation;
import gdg.sharinglog.domain.booking.ReservationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    @EntityGraph(attributePaths = {"member", "member.user"})
    List<Reservation> findAllBySpace_IdAndDateAndStatusOrderByStartTimeAsc(
            Long spaceId,
            LocalDate date,
            ReservationStatus status
    );

    @EntityGraph(attributePaths = {"space", "member", "member.user"})
    @Query("""
            select reservation
            from Reservation reservation
            where reservation.publicId = :publicId
              and reservation.space.group.publicId = :groupPublicId
            """)
    Optional<Reservation> findByPublicIdAndSpaceGroupPublicId(
            @Param("publicId") String publicId,
            @Param("groupPublicId") String groupPublicId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"space", "member", "member.user"})
    @Query("""
            select reservation
            from Reservation reservation
            where reservation.publicId = :publicId
              and reservation.space.group.publicId = :groupPublicId
            """)
    Optional<Reservation> findByPublicIdAndSpaceGroupPublicIdForUpdate(
            @Param("publicId") String publicId,
            @Param("groupPublicId") String groupPublicId
    );
}
