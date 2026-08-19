package gdg.sharinglog.repository;

import java.util.Optional;

import gdg.sharinglog.domain.OAuthProvider;
import gdg.sharinglog.domain.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByProviderAndProviderUserId(OAuthProvider provider, String providerUserId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select account
            from User account
            where account.provider = :provider
              and account.providerUserId = :providerUserId
            """)
    Optional<User> findByProviderAndProviderUserIdForUpdate(
            @Param("provider") OAuthProvider provider,
            @Param("providerUserId") String providerUserId
    );
}
