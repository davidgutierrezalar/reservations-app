package com.reservas.auth;

import com.reservas.auth.AuthDtos.LoginRequest;
import com.reservas.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AuthServiceTest {

    @Test
    void unknownEmailStillPaysForAPasswordHash() {
        UserRepository users = mock(UserRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(encoder.encode(anyString())).thenReturn("dummy-hash");
        when(users.findByEmail(any())).thenReturn(Optional.empty());
        AuthService service = new AuthService(users, encoder, mock(JwtEncoder.class), mock(JwtDecoder.class),
                Duration.ofMinutes(15), Duration.ofDays(7));

        assertThatThrownBy(() -> service.login(new LoginRequest("nobody@test.dev", "password123")))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid email or password");
        verify(encoder).matches("password123", "dummy-hash");
    }
}
