package com.ticketing.user;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import com.ticketing.shared.jpa.AuditableEntity;
import com.ticketing.shared.security.Role;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // JPA only; app code uses the factory
public class User extends AuditableEntity {

    @Id
    private UUID id;

    // stored lower-cased; a functional unique index enforces case-insensitive uniqueness
    @Column(nullable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    private long version;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = jakarta.persistence.FetchType.EAGER)
    private Set<UserRole> roles = new LinkedHashSet<>();

    public User(UUID id, String email, String passwordHash, String displayName) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
    }

    public void addRole(Role role) {
        boolean alreadyHas = roles.stream().anyMatch(r -> r.getRole() == role);
        if (!alreadyHas) {
            roles.add(new UserRole(UUID.randomUUID(), this, role));
        }
    }

    public void removeRole(Role role) {
        roles.removeIf(r -> r.getRole() == role);
    }

    public boolean hasRole(Role role) {
        return roles.stream().anyMatch(r -> r.getRole() == role);
    }

    public boolean isEmailVerified() {
        return emailVerifiedAt != null;
    }
}
