package gdg.sharinglog.repository.booking;

import java.util.List;
import java.util.Optional;

import gdg.sharinglog.domain.booking.Space;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpaceRepository extends JpaRepository<Space, Long> {

    List<Space> findAllByGroup_IdAndActiveTrueOrderByNameAsc(Long groupId);

    boolean existsByGroup_IdAndNameIgnoreCase(Long groupId, String name);

    Optional<Space> findByPublicIdAndGroup_PublicId(String publicId, String groupPublicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select space
            from Space space
            where space.publicId = :publicId
              and space.group.publicId = :groupPublicId
            """)
    Optional<Space> findByPublicIdAndGroupPublicIdForUpdate(
            @Param("publicId") String publicId,
            @Param("groupPublicId") String groupPublicId
    );
}
