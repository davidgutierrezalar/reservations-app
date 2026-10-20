package com.reservas.auth;

import com.reservas.auth.RefreshToken.RevokedReason;
import com.reservas.user.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Opaque refresh tokens with rotation and reuse detection. Each refresh spends the presented token and hands out
 * a new one in the same family; presenting a spent token again revokes the whole family, which signs out both the
 * thief and the real user.
 */
// shortcut: expired and revoked rows are never deleted; add a scheduled cleanup if the table grows
@Service
public class RefreshTokens {

    /** The new token to hand out, and whose session it is. */
    public record Rotation(User user, String token) {
    }

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String SESSION_EXPIRED = "Session expired, sign in again";

    private final RefreshTokenRepository repository;
    private final Duration ttl;
    private final Duration reuseGrace;

    public RefreshTokens(RefreshTokenRepository repository,
                         @Value("${app.jwt.refresh-ttl}") Duration ttl,
                         @Value("${app.jwt.refresh-reuse-grace}") Duration reuseGrace) {
        this.repository = repository;
        this.ttl = ttl;
        this.reuseGrace = reuseGrace;
    }

    /** Starts a new family, on sign-in. */
    @Transactional(propagation = Propagation.MANDATORY)
    public String start(User user) {
        return issue(user, UUID.randomUUID());
    }

    // No rollback on rejection: revoking a family on reuse must stick even though the request fails
    @Transactional(noRollbackFor = BadCredentialsException.class)
    public Rotation rotate(String token) {
        RefreshToken current = repository.findByTokenHash(hash(token))
                .orElseThrow(() -> new BadCredentialsException(SESSION_EXPIRED));
        Instant now = Instant.now();
        if (current.getRevokedAt() != null) {
            if (current.getRevokedReason() == RevokedReason.ROTATED) {
                // Two tabs refreshing at once: the loser gets its own token instead of being treated as a thief
                if (now.isBefore(current.getRevokedAt().plus(reuseGrace))) {
                    return new Rotation(current.getUser(), issue(current.getUser(), current.getFamilyId()));
                }
                repository.revokeFamily(current.getFamilyId(), RevokedReason.REUSED, now);
            }
            throw new BadCredentialsException(SESSION_EXPIRED);
        }
        if (!now.isBefore(current.getExpiresAt())) {
            throw new BadCredentialsException(SESSION_EXPIRED);
        }
        current.revoke(RevokedReason.ROTATED, now);
        return new Rotation(current.getUser(), issue(current.getUser(), current.getFamilyId()));
    }

    /** Signs the whole family out; unknown or already revoked tokens are ignored. */
    @Transactional
    public void revoke(String token) {
        repository.findByTokenHash(hash(token))
                .ifPresent(t -> repository.revokeFamily(t.getFamilyId(), RevokedReason.LOGOUT, Instant.now()));
    }

    private String issue(User user, UUID familyId) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        repository.save(new RefreshToken(user, familyId, hash(token), Instant.now().plus(ttl)));
        return token;
    }

    static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by every Java runtime", e);
        }
    }
}
