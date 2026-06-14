package com.filesync.server.controller;

import com.filesync.common.dto.ForgotPasswordRequestDto;
import com.filesync.common.dto.ResetPasswordRequestDto;
import com.filesync.server.domain.User;
import com.filesync.server.repository.UserRepository;
import com.filesync.server.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private static final Logger log = LoggerFactory.getLogger(UserController.class);
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private static final SecureRandom random = new SecureRandom();

    public UserController(UserRepository userRepository, PasswordEncoder passwordEncoder, EmailService emailService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> payload) {
        String userName = payload.get("username");
        String password = payload.get("password");
        String email = payload.get("email");

        if (userName == null || password == null || email == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing fields"));
        }
        if (userRepository.findByUsername(userName).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Username already exists"));
        }
        if (userRepository.findByEmail(email).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email already registered"));
        }

        User user = new User();
        user.setUsername(userName);
        user.setPassword(passwordEncoder.encode(password));
        user.setEmail(email);
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("message", "User registered successfully"));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody ForgotPasswordRequestDto request) {
        log.info("Forgot password request for email: {}", request.getEmail());
        Optional<User> optionalUser = userRepository.findByEmail(request.getEmail());

        if (optionalUser.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email not found"));
        }

        User user = optionalUser.get();
        int tokenValue = 100_000 + random.nextInt(900_000);
        String plainToken = String.valueOf(tokenValue);
        String hashedToken = passwordEncoder.encode(plainToken);
        user.setResetToken(hashedToken);
        user.setTokenExpiry(LocalDateTime.now().plusHours(1));
        userRepository.save(user);

        try {
            emailService.sendResetToken(user.getEmail(), plainToken);
            log.info("Reset token sent to {}", user.getEmail());
            return ResponseEntity.ok(Map.of("message", "Reset code sent to your email"));
        } catch (Exception e) {
            log.error("Failed to send reset email to {}: {}", user.getEmail(), e.getMessage(), e);
            log.error("Failed to send email to {}: {}", user.getEmail(), e.getMessage(), e);
            e.printStackTrace();
            // Rollback token (optional)
            user.setResetToken(null);
            user.setTokenExpiry(null);
            userRepository.save(user);
            return ResponseEntity.status(500).body(Map.of("error", "Failed to send reset code. Please try again later."));
        }
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody ResetPasswordRequestDto request) {
        log.info("Reset password request for email: {}", request.getEmail());

        if (request.getEmail() == null || request.getToken() == null || request.getNewPassword() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing email, token, or password"));
        }

        Optional<User> opt = userRepository.findByEmail(request.getEmail());
        if (opt.isEmpty()) {
            log.warn("Reset attempt for non-existent email: {}", request.getEmail());
            return ResponseEntity.badRequest().body(Map.of("error", "Email address not found"));
        }

        User user = opt.get();
        if (user.getResetToken() == null || user.getTokenExpiry().isBefore(LocalDateTime.now())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Token expired or not found"));
        }

        if (!passwordEncoder.matches(request.getToken(), user.getResetToken())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid token"));
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setResetToken(null);
        user.setTokenExpiry(null);
        userRepository.save(user);

        log.info("Password reset successfully for user: {}", user.getUsername());
        return ResponseEntity.ok(Map.of("message", "Password reset successfully"));
    }

    @GetMapping("/search")
    public ResponseEntity<?> searchUsers(@RequestParam("q") String query) {
        if (query == null || query.trim().isEmpty()) {
            return ResponseEntity.ok(List.of());
        }
        List<User> users = userRepository.findByUsernameContainingIgnoreCaseOrEmailContainingIgnoreCase(query, query);
        // Return only necessary fields (don't expose passwords)
        List<Map<String, String>> result = users.stream()
                .map(user -> Map.of(
                        "username", user.getUsername(),
                        "email", user.getEmail()
                ))
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }
}