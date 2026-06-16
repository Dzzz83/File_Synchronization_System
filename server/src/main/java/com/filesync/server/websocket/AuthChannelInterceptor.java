package com.filesync.server.websocket;

import com.filesync.server.security.JwtService;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class AuthChannelInterceptor implements ChannelInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AuthChannelInterceptor.class);
    private final JwtService jwtService;

    public AuthChannelInterceptor(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String token = extractToken(accessor);
            if (token != null && jwtService.validateToken(token)) {
                try {
                    Claims claims = jwtService.extractAllClaims(token);
                    String username = claims.getSubject();
                    List<String> roles = claims.get("roles", List.class);
                    List<GrantedAuthority> authorities = roles.stream()
                            .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                            .collect(Collectors.toList());
                    UserDetails userDetails = new User(username, "", authorities);
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(userDetails, null, authorities);
                    accessor.setUser(authentication);
                    log.info("WebSocket authenticated user: {}", username);
                } catch (Exception e) {
                    log.warn("Error processing token for WebSocket: {}", e.getMessage());
                    throw new SecurityException("Invalid token");
                }
            } else {
                log.warn("WebSocket CONNECT without valid token");
                throw new SecurityException("Missing or invalid token");
            }
        }
        return message;
    }

    private String extractToken(StompHeaderAccessor accessor) {
        String token = accessor.getFirstNativeHeader("token");
        if (token == null) {
            String authHeader = accessor.getFirstNativeHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                token = authHeader.substring(7);
            }
        }
        return token;
    }
}