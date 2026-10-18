package com.reservas.config;

import com.reservas.user.Role;
import com.reservas.user.User;
import com.reservas.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Admins cannot self-register, so the first one comes from configuration. */
@Component
class AdminInitializer implements ApplicationRunner {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String password;

    AdminInitializer(UserRepository users, PasswordEncoder passwordEncoder,
                     @Value("${app.admin.email}") String email, @Value("${app.admin.password}") String password) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.email = email.trim().toLowerCase();
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (email.isEmpty() || password.isEmpty() || users.existsByEmail(email)) {
            return;
        }
        users.save(new User("Admin", email, passwordEncoder.encode(password), Role.ADMIN));
    }
}
