package gdg.sharinglog.repository;

import java.util.List;
import java.util.Optional;

import gdg.sharinglog.domain.GroupInvitation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupInvitationRepository extends JpaRepository<GroupInvitation, Long> {

    boolean existsByCodeHash(String codeHash);

    Optional<GroupInvitation> findByCodeHash(String codeHash);

    List<GroupInvitation> findAllByGroup_Id(Long groupId);
}
