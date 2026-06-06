package com.p2plending.notification.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;

public record AuthenticatedUser(
        String userId,
        String username,
        Collection<? extends GrantedAuthority> authorities
) implements UserDetails {

    @Override public String getUsername() { return username; }
    @Override public String getPassword() { return null; }
    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return authorities; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return true; }
}
