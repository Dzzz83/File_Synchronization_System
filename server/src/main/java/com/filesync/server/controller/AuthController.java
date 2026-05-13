package com.filesync.server.controller;

import com.filesync.server.domain.User;
import com.filesync.server.repository.UserRepository;
import com.filesync.server.security.JwtService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController
{
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthController(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService)
    {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> credentials)
    {
        // get username and password
        String username = credentials.get("username");
        String password = credentials.get("password");

        // return error if 1 of 2 fields missing
        if (username == null || password == null)
        {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Missing username or password"));
        }

        // get the user from userRepo
        User user = userRepository.findByUsername(username).orElse(null);
        // if no user found or wrong password
        if (user == null || !passwordEncoder.matches(password, user.getPassword()))
        {
            return ResponseEntity.status(401)
                    .body(Map.of("error", "Invalid credentials"));
        }
        // generate token for that login session
        String token = jwtService.generateToken(username);
        return ResponseEntity.ok(Map.of("token", token, "username", username));
    }
}
