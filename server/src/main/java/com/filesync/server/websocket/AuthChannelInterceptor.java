package com.filesync.server.websocket;

import com.filesync.server.security.JwtService;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.util.Collections;

@Component
public class AuthChannelInterceptor implements ChannelInterceptor {

    private final JwtService jwtService;

    public AuthChannelInterceptor(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authHeader = accessor.getFirstNativeHeader("Authorization");
            System.out.println("AuthChannelInterceptor: CONNECT frame received, Authorization header: " + authHeader);
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                try {
                    if (jwtService.validateToken(token)) {
                        String username = jwtService.extractUsername(token);
                        System.out.println("AuthChannelInterceptor: Token valid for user: " + username);
                        UserDetails userDetails = User.withUsername(username)
                                .password("")
                                .authorities(Collections.emptyList())
                                .build();
                        UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                        accessor.setUser(authentication);
                        System.out.println("AuthChannelInterceptor: Principal set for session: " + accessor.getSessionId());
                    } else {
                        System.err.println("AuthChannelInterceptor: Token validation failed");
                    }
                } catch (Exception e) {
                    System.err.println("AuthChannelInterceptor: Exception while validating token: " + e.getMessage());
                }
            } else {
                System.err.println("AuthChannelInterceptor: Missing or invalid Authorization header");
            }
        }
        return message;
    }
}