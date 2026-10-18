package com.reservas.auth;

import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** The caller as read from the validated JWT. */
public record CurrentUser(Long id, boolean admin) {

    public static CurrentUser from(JwtAuthenticationToken auth) {
        boolean admin = auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        return new CurrentUser(Long.valueOf(auth.getToken().getSubject()), admin);
    }
}
