package com.reservas.auth;

import com.reservas.auth.AuthDtos.LoginRequest;
import com.reservas.auth.AuthDtos.RegisterRequest;
import com.reservas.auth.AuthDtos.UserResponse;
import com.reservas.auth.AuthService.Tokens;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

/**
 * Tokens travel only in httpOnly cookies, so page scripts (and any XSS) never see them.
 * The refresh cookie is scoped to /api/auth, so it is not sent with ordinary API calls.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    public static final String ACCESS_COOKIE = "access_token";
    static final String REFRESH_COOKIE = "refresh_token";
    // Covers /refresh and /logout, which must see it to revoke the session
    private static final String REFRESH_PATH = "/api/auth";
    // shortcut: refresh cookies before rotation lived here; browsers send that one first and it can never succeed.
    // Expire it on every sign-in, refresh and logout; delete this line once those 7-day cookies have all expired
    private static final String LEGACY_REFRESH_PATH = "/api/auth/refresh";

    private final AuthService authService;
    private final Duration accessTtl;
    private final Duration refreshTtl;
    private final boolean secureCookies;

    public AuthController(AuthService authService,
                          @Value("${app.jwt.ttl}") Duration accessTtl,
                          @Value("${app.jwt.refresh-ttl}") Duration refreshTtl,
                          @Value("${app.cookie.secure}") boolean secureCookies) {
        this.authService = authService;
        this.accessTtl = accessTtl;
        this.refreshTtl = refreshTtl;
        this.secureCookies = secureCookies;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest req, HttpServletResponse res) {
        return signIn(authService.register(req), res);
    }

    @PostMapping("/login")
    public UserResponse login(@Valid @RequestBody LoginRequest req, HttpServletResponse res) {
        return signIn(authService.login(req), res);
    }

    /** Also how the app learns who is signed in when it loads, since it cannot read the cookies. */
    @PostMapping("/refresh")
    public UserResponse refresh(@CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken,
                                HttpServletResponse res) {
        return signIn(authService.refresh(refreshToken), res);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken,
                       HttpServletResponse res) {
        authService.logout(refreshToken);
        res.addHeader(HttpHeaders.SET_COOKIE, cookie(ACCESS_COOKIE, "", "/api", Duration.ZERO));
        res.addHeader(HttpHeaders.SET_COOKIE, cookie(REFRESH_COOKIE, "", REFRESH_PATH, Duration.ZERO));
        res.addHeader(HttpHeaders.SET_COOKIE, cookie(REFRESH_COOKIE, "", LEGACY_REFRESH_PATH, Duration.ZERO));
    }

    private UserResponse signIn(Tokens tokens, HttpServletResponse res) {
        res.addHeader(HttpHeaders.SET_COOKIE, cookie(ACCESS_COOKIE, tokens.accessToken(), "/api", accessTtl));
        res.addHeader(HttpHeaders.SET_COOKIE, cookie(REFRESH_COOKIE, tokens.refreshToken(), REFRESH_PATH, refreshTtl));
        res.addHeader(HttpHeaders.SET_COOKIE, cookie(REFRESH_COOKIE, "", LEGACY_REFRESH_PATH, Duration.ZERO));
        return tokens.user();
    }

    private String cookie(String name, String value, String path, Duration maxAge) {
        return ResponseCookie.from(name, value)
                .httpOnly(true).secure(secureCookies).sameSite("Strict").path(path).maxAge(maxAge)
                .build().toString();
    }
}
