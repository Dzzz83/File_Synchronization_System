package com.filesync.server.controller;

import com.filesync.server.domain.User;
import com.filesync.server.security.JwtService;
import com.filesync.server.service.UserFindService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

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
        User user = userFindService.findByLogin(username);
        // if no user found or wrong password
        if (user == null || !passwordEncoder.matches(password, user.getPassword()))
        {
            return ResponseEntity.status(401)
                    .body(Map.of("error", "Invalid credentials"));
        }
        // generate token for that login session
        String token = jwtService.generateToken(user.getUsername());
        return ResponseEntity.ok(Map.of("token", token, "username", user.getUsername()));
    }
}