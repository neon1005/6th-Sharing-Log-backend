package gdg.sharinglog.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@Table(
        name = "users",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_users_provider_user_id",
                columnNames = {"provider", "provider_user_id"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Entity
public class User implements UserDetails {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 20)
    private OAuthProvider provider;

    @Column(name = "provider_user_id", nullable = false)
    private String providerUserId;

    @Column(name = "email")
    private String email;

    @Column(name = "password")
    private String password;

    @Column(name = "nickname")
    private String nickname;

    @Column(name = "due_soon_push_enabled", nullable = false)
    private boolean dueSoonPushEnabled = true;

    @Column(name = "chore_completed_push_enabled", nullable = false)
    private boolean choreCompletedPushEnabled = true;

    @Column(name = "notice_push_enabled", nullable = false)
    private boolean noticePushEnabled = false;

    @Builder
    public User(OAuthProvider provider, String providerUserId, String email,
                String password, String nickname) {
        this.provider = provider;
        this.providerUserId = providerUserId;
        this.email = email;
        this.password = password;
        this.nickname = nickname;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("user"));
    }

    @Override
    public String getUsername() {
        return provider.name().toLowerCase() + ":" + providerUserId;
    }

    @Override
    public String getPassword() {
        return password;
    }
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    public User update(String nickname) {
        this.nickname = nickname;
        return this;
    }

    public User updateOAuthProfile(String email) {
        this.email = email;
        return this;
    }

    public void updateNotificationPreferences(
            Boolean dueSoonPushEnabled,
            Boolean choreCompletedPushEnabled,
            Boolean noticePushEnabled
    ) {
        if (dueSoonPushEnabled != null) {
            this.dueSoonPushEnabled = dueSoonPushEnabled;
        }
        if (choreCompletedPushEnabled != null) {
            this.choreCompletedPushEnabled = choreCompletedPushEnabled;
        }
        if (noticePushEnabled != null) {
            this.noticePushEnabled = noticePushEnabled;
        }
    }
}
