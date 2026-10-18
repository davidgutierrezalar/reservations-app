package com.reservas.auth;

import com.reservas.auth.AuthDtos.AuthResponse;
import com.reservas.auth.AuthDtos.LoginRequest;
import com.reservas.auth.AuthDtos.RegisterRequest;
import com.reservas.auth.AuthDtos.UserResponse;
import com.reservas.common.ApiException;
import com.reservas.user.Role;
import com.reservas.user.User;
import com.reservas.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final Duration ttl;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtEncoder jwtEncoder,
                       @Value("${app.jwt.ttl}") Duration ttl) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.ttl = ttl;
    }

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        String email = req.email().trim().toLowerCase();
        if (users.existsByEmail(email)) {
            throw ApiException.conflict("Email already registered");
        }
        User user = users.save(new User(req.name().trim(), email, passwordEncoder.encode(req.password()), Role.USER));
        return issueToken(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest req) {
        User user = users.findByEmail(req.email().trim().toLowerCase())
                .filter(u -> passwordEncoder.matches(req.password(), u.getPassword()))
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        return issueToken(user);
    }

    private AuthResponse issueToken(User user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(ttl);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim("name", user.getName())
                .claim("roles", List.of(user.getRole().name()))
                .build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        return new AuthResponse(token, expiresAt, UserResponse.from(user));
    }
}
