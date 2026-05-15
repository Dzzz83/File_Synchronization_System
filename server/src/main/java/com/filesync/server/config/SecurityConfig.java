package com.filesync.server.config;

import com.filesync.server.repository.UserRepository;
import com.filesync.server.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final UserDetailsService monitoringUserDetailsService;  // <-- inject

    public SecurityConfig(JwtAuthenticationFilter jwtAuthFilter,
                          UserDetailsService monitoringUserDetailsService) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.monitoringUserDetailsService = monitoringUserDetailsService;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // Your existing UserDetailsService for the main JWT authentication
    @Bean
    public UserDetailsService userDetailsService(UserRepository userRepo) {
        return username -> {
            com.filesync.server.domain.User user = userRepo.findByUsername(username)
                    .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
            return User.withUsername(user.getUsername())
                    .password(user.getPassword())
                    .authorities("USER")
                    .build();
        };
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/auth/login",
                                "/api/users/register",
                                "/api/users/forgot-password",
                                "/api/users/reset-password",
                                "/health"
                        ).permitAll()
                        .requestMatchers("/actuator/prometheus").hasRole("METRICS")  // <-- protected
                        .anyRequest().authenticated()
                )
                .httpBasic(withDefaults())   // <-- enable Basic Auth
                .userDetailsService(monitoringUserDetailsService) // <-- use the in‑memory user
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}