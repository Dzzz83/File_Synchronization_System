package com.filesync.server.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

@Configuration
public class MonitoringUserDetailsService {

    @Value("${METRICS_PASSWORD}")
    private String metricsPassword;

    @Bean
    public UserDetailsService monitoringUserDetailsService() {
        InMemoryUserDetailsManager manager = new InMemoryUserDetailsManager();
        manager.createUser(User.withUsername("metrics")
                .password("{bcrypt}" + new BCryptPasswordEncoder().encode(metricsPassword))
                .roles("METRICS")
                .build());
        return manager;
    }
}