package com.shopflow.user_service.security;

import com.shopflow.user_service.domain.User;
import com.shopflow.user_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Loads users for Spring Security's authentication manager.
 *
 * We namespace the username as "email:tenantId" so the same email
 * address can exist in multiple tenants without collision.
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // username format: "email:tenantId"
        String[] parts = username.split(":", 2);
        if (parts.length != 2) {
            throw new UsernameNotFoundException("Invalid username format");
        }

        String email    = parts[0];
        UUID   tenantId = UUID.fromString(parts[1]);

        User user = userRepository.findByEmailWithRoles(email, tenantId)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "User not found: " + email));

        List<SimpleGrantedAuthority> authorities = user.getUserRoles().stream()
                .map(ur -> new SimpleGrantedAuthority("ROLE_" + ur.getRole().getName().name()))
                .toList();

        return new org.springframework.security.core.userdetails.User(
                user.getId().toString(),   // principal = userId (used by AuditorAware)
                user.getPassword(),
                user.getStatus() == User.UserStatus.ACTIVE,  // enabled
                true,   // accountNonExpired
                true,   // credentialsNonExpired
                user.getStatus() != User.UserStatus.SUSPENDED, // accountNonLocked
                authorities
        );
    }
}