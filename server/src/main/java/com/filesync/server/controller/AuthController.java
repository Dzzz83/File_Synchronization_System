package com.filesync.server.controller;

import com.filesync.server.domain.User;
import com.filesync.server.security.JwtService;
import com.filesync.server.service.UserFindService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/auth")
public class AuthController
{
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserFindService userFindService;

    public AuthController(JwtService jwtService,
                          UserFindService userFindService,
                          PasswordEncoder passwordEncoder) {
        this.jwtService = jwtService;
        this.userFindService = userFindService;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> credentials)
    {
        String username = credentials.get("username");
        String password = credentials.get("password");

        if (username == null || password == null)
        {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Missing username or password"));
        }

        User user = userFindService.findByLogin(username);
        if (user == null || !passwordEncoder.matches(password, user.getPassword()))
        {
            return ResponseEntity.status(401)
                    .body(Map.of("error", "Invalid credentials"));
        }

        // Extract roles from user entity – roles are already strings
        List<String> roles = new ArrayList<>(user.getRoles());

        // Generate token with embedded roles (fully stateless)
        String token = jwtService.generateToken(user.getUsername(), roles);

        return ResponseEntity.ok(Map.of(
                "token", token,
                "username", user.getUsername()
        ));
    }
}