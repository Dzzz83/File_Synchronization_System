package com.filesync.server.controller;

import com.filesync.common.dto.ForgotPasswordRequestDto;
import com.filesync.common.dto.ResetPasswordRequestDto;
import com.filesync.server.domain.User;
import com.filesync.server.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
public class UserController
{
    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserController(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> payload)
    {
        String userName = payload.get("username");
        String password = payload.get("password");
        String email = payload.get("email");

        if (userName == null || password == null || email == null)
        {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing fields"));
        }
        if (userRepository.findByUsername(userName).isPresent())
        {
            return ResponseEntity.badRequest().body(Map.of("error", "Users already existed"));
        }
        if (userRepository.findByEmail(email).isPresent())
        {
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
    public ResponseEntity<?> forgotPassword(@RequestBody ForgotPasswordRequestDto request)
    {
        log.info("Forgot password request for email: {}", request.getEmail());
        Optional<User> optionalUser = userRepository.findByEmail(request.getEmail());
        if (optionalUser.isEmpty())
        {
            log.warn("Email not found: {}", request.getEmail());
            return ResponseEntity.badRequest().body(Map.of("error", "Email not found"));
        }
        User user = optionalUser.get();
        String token = UUID.randomUUID().toString();
        user.setResetToken(token);
        user.setTokenExpiry(LocalDateTime.now().plusHours(1));
        userRepository.save(user);
        log.info("Generated token {} for user: {}", token, user.getUsername());
        return ResponseEntity.ok(Map.of("message", "Reset token generated", "token", token));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody ResetPasswordRequestDto request) {
        log.info("Reset password request received with token: {}", request.getToken());
        if (request.getToken() == null || request.getNewPassword() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing token or password"));
        }
        Optional<User> opt = userRepository.findByResetToken(request.getToken());
        if (opt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid token"));
        }
        User user = opt.get();
        if (user.getTokenExpiry().isBefore(LocalDateTime.now())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Token expired"));
        }
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setResetToken(null);
        user.setTokenExpiry(null);
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("message", "Password reset successfully"));
    }
}