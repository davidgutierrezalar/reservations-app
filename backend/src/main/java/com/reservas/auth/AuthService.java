package com.reservas.auth;

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

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final RefreshTokens refreshTokens;
    private final Duration accessTtl;
    private final String dummyHash;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtEncoder jwtEncoder,
                       RefreshTokens refreshTokens, @Value("${app.jwt.ttl}") Duration accessTtl) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.refreshTokens = refreshTokens;
        this.accessTtl = accessTtl;
        this.dummyHash = passwordEncoder.encode("timing-equalizer");
    }

    /** A signed-in session: the user plus the two tokens the controller puts in cookies. */
    public record Tokens(String accessToken, String refreshToken, UserResponse user) {
    }

    @Transactional
    public Tokens register(RegisterRequest req) {
        String email = req.email().trim().toLowerCase();
        // BCrypt rejects more than 72 bytes, and multibyte characters pass the 72-character limit on the DTO
        if (tooLongForBcrypt(req.password())) {
            throw ApiException.badRequest("Validation failed")
                    .with("errors", Map.of("password", "must be at most 72 bytes; accented characters count double"));
        }
        if (users.existsByEmail(email)) {
            throw ApiException.conflict("Email already registered");
        }
        User user = users.save(new User(req.name().trim(), email, passwordEncoder.encode(req.password()), Role.USER));
        return new Tokens(accessToken(user), refreshTokens.start(user), UserResponse.from(user));
    }

    @Transactional
    public Tokens login(LoginRequest req) {
        User user = users.findByEmail(req.email().trim().toLowerCase()).orElse(null);
        // Hash even when the email is unknown, so response time does not reveal which emails are registered
        boolean matches = !tooLongForBcrypt(req.password())
                && passwordEncoder.matches(req.password(), user != null ? user.getPassword() : dummyHash);
        if (user == null || !matches) {
            throw new BadCredentialsException("Invalid email or password");
        }
        return new Tokens(accessToken(user), refreshTokens.start(user), UserResponse.from(user));
    }

    /** Swaps a refresh token for a new pair, reloading the user so role and name changes apply. */
    @Transactional(noRollbackFor = BadCredentialsException.class)
    public Tokens refresh(String refreshToken) {
        if (refreshToken == null) {
            throw new BadCredentialsException("Not signed in");
        }
        RefreshTokens.Rotation rotation = refreshTokens.rotate(refreshToken);
        return new Tokens(accessToken(rotation.user()), rotation.token(), UserResponse.from(rotation.user()));
    }

    public void logout(String refreshToken) {
        if (refreshToken != null) {
            refreshTokens.revoke(refreshToken);
        }
    }

    private static boolean tooLongForBcrypt(String password) {
        return password.getBytes(StandardCharsets.UTF_8).length > 72;
    }

    // shortcut: access tokens stay valid until they expire (15 min) even after logout; keep the TTL short
    private String accessToken(User user) {
        Instant now = Instant.now();
        return encode(JwtClaimsSet.builder()
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(now.plus(accessTtl))
                .claim("token_use", "access")
                .claim("name", user.getName())
                .claim("roles", List.of(user.getRole().name()))
                .build());
    }

    private String encode(JwtClaimsSet claims) {
        return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
}
