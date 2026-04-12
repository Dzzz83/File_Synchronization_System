package com.filesync.server.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints
                        .requestMatchers("/health", "/api/**", "/h2-console/**").permitAll()
                        // All other requests need authentication
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form.disable())
                // Allow H2 console to be displayed in a frame
                .headers(headers -> headers.frameOptions(frame -> frame.disable()));
        System.out.println("SecurityConfig loaded - custom filter chain");
        return http.build();
    }
}