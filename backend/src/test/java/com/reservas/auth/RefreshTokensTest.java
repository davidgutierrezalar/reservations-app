package com.reservas.auth;

import com.reservas.auth.RefreshToken.RevokedReason;
import com.reservas.user.Role;
import com.reservas.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class RefreshTokensTest {

    private final RefreshTokenRepository repository = mock(RefreshTokenRepository.class);
    private final User user = new User("Ana", "ana@test.dev", "hash", Role.USER);
    private final UUID family = UUID.randomUUID();

    private RefreshTokens service(Duration grace) {
        return new RefreshTokens(repository, Duration.ofDays(7), grace);
    }

    private RefreshToken stored(String token, Instant expiresAt) {
        RefreshToken t = new RefreshToken(user, family, RefreshTokens.hash(token), expiresAt);
        when(repository.findByTokenHash(RefreshTokens.hash(token))).thenReturn(Optional.of(t));
        return t;
    }

    @Test
    void rotationSpendsTheTokenAndIssuesANewOneInTheSameFamily() {
        RefreshToken current = stored("t1", Instant.now().plus(Duration.ofDays(1)));

        RefreshTokens.Rotation rotation = service(Duration.ZERO).rotate("t1");

        assertThat(current.getRevokedReason()).isEqualTo(RevokedReason.ROTATED);
        assertThat(rotation.user()).isSameAs(user);
        assertThat(rotation.token()).isNotEqualTo("t1");
        verify(repository).save(argThat(t -> t.getFamilyId().equals(family) && t.getRevokedAt() == null));
    }

    @Test
    void presentingASpentTokenAfterTheGraceRevokesTheFamily() {
        RefreshToken spent = stored("t1", Instant.now().plus(Duration.ofDays(1)));
        spent.revoke(RevokedReason.ROTATED, Instant.now().minusSeconds(60));

        assertThatThrownBy(() -> service(Duration.ofSeconds(10)).rotate("t1")).isInstanceOf(BadCredentialsException.class);

        verify(repository).revokeFamily(eq(family), eq(RevokedReason.REUSED), any());
        verify(repository, never()).save(any());
    }

    @Test
    void aParallelRefreshWithinTheGraceGetsItsOwnToken() {
        RefreshToken spent = stored("t1", Instant.now().plus(Duration.ofDays(1)));
        spent.revoke(RevokedReason.ROTATED, Instant.now());

        RefreshTokens.Rotation rotation = service(Duration.ofSeconds(10)).rotate("t1");

        assertThat(rotation.user()).isSameAs(user);
        verify(repository, never()).revokeFamily(any(), any(), any());
    }

    @Test
    void loggedOutAndExpiredTokensAreRejectedWithoutANewToken() {
        stored("gone", Instant.now().plus(Duration.ofDays(1))).revoke(RevokedReason.LOGOUT, Instant.now());
        stored("old", Instant.now().minusSeconds(1));
        when(repository.findByTokenHash(RefreshTokens.hash("unknown"))).thenReturn(Optional.empty());

        for (String token : new String[] {"gone", "old", "unknown"}) {
            assertThatThrownBy(() -> service(Duration.ofSeconds(10)).rotate(token))
                    .isInstanceOf(BadCredentialsException.class);
        }
        verify(repository, never()).save(any());
        verify(repository, never()).revokeFamily(any(), any(), any());
    }

    @Test
    void onlyTheHashIsStored() {
        String token = service(Duration.ZERO).start(user);

        verify(repository).save(argThat(t -> !token.equals(RefreshTokens.hash(token))));
        assertThat(RefreshTokens.hash(token)).hasSize(64).doesNotContain(token);
        verify(repository, never()).findByTokenHash(anyString());
    }
}
