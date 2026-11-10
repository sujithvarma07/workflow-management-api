package com.relic.backend.security;

import com.relic.backend.entity.User;
import com.relic.backend.entity.UserRole;
import com.relic.backend.entity.UserStatus;
import com.relic.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates exactly one ADMIN account on startup if none exists yet, from
 * required environment variables with no defaults - the same "no committed
 * credentials" rule the rest of this app's configuration follows. Without
 * this, there would be no way to log in at all on a fresh database, since
 * every other account either self-registers as a DEVELOPER or is created
 * by an existing ADMIN.
 */
@Component
@RequiredArgsConstructor
public class AdminBootstrapSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.security.admin-bootstrap-username}")
    private String adminUsername;

    @Value("${app.security.admin-bootstrap-email}")
    private String adminEmail;

    @Value("${app.security.admin-bootstrap-password}")
    private String adminPassword;

    @Override
    @Transactional
    public void run(String... args) {
        if (userRepository.existsByRole(UserRole.ADMIN)) {
            return;
        }

        User admin = User.builder()
                .username(adminUsername)
                .email(adminEmail)
                .fullName("System Administrator")
                .role(UserRole.ADMIN)
                .passwordHash(passwordEncoder.encode(adminPassword))
                .status(UserStatus.ACTIVE)
                .build();

        userRepository.save(admin);
    }
}
