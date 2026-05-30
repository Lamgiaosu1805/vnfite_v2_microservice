package com.p2plending.loan.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;

/**
 * Immutable principal populated from a validated JWT issued by auth-service.
 * Available via @AuthenticationPrincipal in controllers.
 */
public record AuthenticatedUser(
        String userId,
        String email,
        Collection<? extends GrantedAuthority> authorities
) implements UserDetails {

    @Override public String getUsername()  { return email; }
    @Override public String getPassword()  { return null; }
    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return authorities; }
    @Override public boolean isAccountNonExpired()     { return true; }
    @Override public boolean isAccountNonLocked()      { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled()               { return true; }
}
