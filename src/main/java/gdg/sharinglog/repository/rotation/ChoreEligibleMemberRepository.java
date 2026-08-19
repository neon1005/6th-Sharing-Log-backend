package gdg.sharinglog.repository.rotation;

import java.util.List;
import java.util.Optional;

import gdg.sharinglog.domain.rotation.ChoreEligibleMember;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChoreEligibleMemberRepository extends JpaRepository<ChoreEligibleMember, Long> {

    @EntityGraph(attributePaths = {"member", "member.user"})
    List<ChoreEligibleMember> findAllByChore_IdOrderById(Long choreId);

    @EntityGraph(attributePaths = {"member", "member.user"})
    List<ChoreEligibleMember> findAllByChore_IdAndEnabledTrueOrderById(Long choreId);

    @EntityGraph(attributePaths = {"chore", "chore.group", "member", "member.user"})
    List<ChoreEligibleMember> findAllByMember_IdOrderByChore_Id(Long membershipId);

    @EntityGraph(attributePaths = {"chore", "chore.group", "member", "member.user"})
    Optional<ChoreEligibleMember> findByChore_IdAndMember_Id(Long choreId, Long membershipId);
}
