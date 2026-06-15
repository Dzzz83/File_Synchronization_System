package com.filesync.server.config;

import com.filesync.server.domain.User;
import com.filesync.server.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AdminInitializer {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${admin.username:admin}")
    private String adminUsername;

    @Value("${admin.email:admin@example.com}")
    private String adminEmail;

    // Read from environment variable (loaded from .env by spring-dotenv)
    @Value("${ADMIN_PASSWORD:}")
    private String adminPassword;

    public AdminInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void createAdminIfNotExists() {
        boolean adminExists = userRepository.findAll().stream().anyMatch(User::getIsAdmin);
        if (adminExists) {
            return;
        }

        if (adminPassword == null || adminPassword.trim().isEmpty()) {
            System.err.println("WARNING: ADMIN_PASSWORD not set in .env. Admin user not created.");
            return;
        }

        User admin = new User();
        admin.setUsername(adminUsername);
        admin.setPassword(passwordEncoder.encode(adminPassword));
        admin.setEmail(adminEmail);
        admin.setIsAdmin(true);
        admin.setIsDemo(false);
        admin.setTotalStorageBytes(0L);
        admin.setFileCount(0);
        admin.setMaxStorageBytes(0L);
        admin.setMaxFileCount(0);

        userRepository.save(admin);
        System.out.println("Admin account created: " + adminUsername);
    }
}