package com.filesync.server.web;

import com.filesync.server.domain.User;
import com.filesync.server.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Controller
public class AuthController {

    @Autowired private UserRepository userRepo;
    @Autowired private PasswordEncoder passwordEncoder;

    @GetMapping("/register")
    public String registerForm(Model model) {
        model.addAttribute("user", new User());
        return "register";
    }

    @PostMapping("/register")
    public String registerUser(@Valid User user, BindingResult result, Model model) {
        if (userRepo.findByUsername(user.getUsername()).isPresent()) {
            result.rejectValue("username", "error.user", "Username already exists");
        }
        if (userRepo.findByEmail(user.getEmail()).isPresent()) {
            result.rejectValue("email", "error.user", "Email already registered");
        }
        if (result.hasErrors()) {
            return "register";
        }
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        userRepo.save(user);
        return "redirect:/login";
    }

    @GetMapping("/login")
    public String login(HttpServletRequest request, HttpServletResponse response) {
        System.out.println("=== AuthController.login() called ===");
        System.out.println("Request URI: " + request.getRequestURI());
        System.out.println("Session ID: " + request.getSession().getId());
        return "login";
    }

    @GetMapping("/forgot-password")
    public String forgotPasswordForm() {
        return "forgot-password";
    }

    @PostMapping("/forgot-password")
    public String processForgotPassword(@RequestParam("email") String email, Model model) {
        Optional<User> opt = userRepo.findByEmail(email);
        if (opt.isPresent()) {
            User user = opt.get();
            String token = UUID.randomUUID().toString();
            user.setResetToken(token);
            user.setTokenExpiry(LocalDateTime.now().plusHours(1));
            userRepo.save(user);
            model.addAttribute("message", "Password reset link sent to " + email);
        } else {
            model.addAttribute("error", "Email not found");
        }
        return "forgot-password";
    }

    @GetMapping("/reset-password")
    public String resetPasswordForm(@RequestParam("token") String token, Model model) {
        Optional<User> opt = userRepo.findByResetToken(token);
        if (opt.isEmpty() || opt.get().getTokenExpiry().isBefore(LocalDateTime.now())) {
            model.addAttribute("error", "Invalid or expired token");
            return "forgot-password";
        }
        model.addAttribute("token", token);
        return "reset-password";
    }

    @PostMapping("/reset-password")
    public String resetPassword(@RequestParam("token") String token, @RequestParam("password") String password, Model model) {
        Optional<User> opt = userRepo.findByResetToken(token);
        if (opt.isEmpty() || opt.get().getTokenExpiry().isBefore(LocalDateTime.now())) {
            model.addAttribute("error", "Invalid or expired token");
            return "forgot-password";
        }
        User user = opt.get();
        user.setPassword(passwordEncoder.encode(password));
        user.setResetToken(null);
        user.setTokenExpiry(null);
        userRepo.save(user);
        return "redirect:/login?resetSuccess";
    }
}