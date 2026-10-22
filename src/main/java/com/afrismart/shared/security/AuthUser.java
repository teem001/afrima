package com.afrismart.shared.security;

import java.util.List;
import java.util.UUID;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;


public record AuthUser(UUID id, String email, Role role) {

    public static AuthUser current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthUser user)) {
            return null;
        }
        return user;
    }

    public static AuthUser require() {
        AuthUser user = current();
        if (user == null) {
            throw new org.springframework.security.authentication.InsufficientAuthenticationException("Authentication required");
        }
        return user;
    }

    public Authentication toAuthentication() {
        return new UsernamePasswordAuthenticationToken(this, null, List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
    }
}
