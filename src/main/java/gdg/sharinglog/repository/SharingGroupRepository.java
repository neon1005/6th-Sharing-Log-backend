package gdg.sharinglog.repository;

import java.util.Optional;

import gdg.sharinglog.domain.SharingGroup;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SharingGroupRepository extends JpaRepository<SharingGroup, Long> {

    @Query("""
            select sharingGroup
            from SharingGroup sharingGroup
            where sharingGroup.publicId = :publicId
              and sharingGroup.deletedAt is null
            """)
    Optional<SharingGroup> findByPublicId(@Param("publicId") String publicId);

    @Override
    @Query("""
            select sharingGroup
            from SharingGroup sharingGroup
            where sharingGroup.id = :id
              and sharingGroup.deletedAt is null
            """)
    Optional<SharingGroup> findById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select sharingGroup
            from SharingGroup sharingGroup
            where sharingGroup.publicId = :publicId
              and sharingGroup.deletedAt is null
            """)
    Optional<SharingGroup> findByPublicIdForUpdate(@Param("publicId") String publicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select sharingGroup
            from SharingGroup sharingGroup
            where sharingGroup.id = :groupId
              and sharingGroup.deletedAt is null
            """)
    Optional<SharingGroup> findByIdForUpdate(@Param("groupId") Long groupId);
}
