package com.filesync.server.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter
{
    private final JwtService jwtService;
    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private final List<String> publicPaths = List.of(
            "/api/auth/login",
            "/api/users/register",
            "/api/users/forgot-password",
            "/api/users/reset-password",
            "/health",
            "/monitoring",
            "/debug/"
    );

    public JwtAuthenticationFilter(JwtService jwtService)
    {
        this.jwtService = jwtService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request)
    {
        String path = request.getRequestURI();
        return publicPaths.stream().anyMatch(path::startsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException
    {
        final String authHeader = request.getHeader("Authorization");

        // Case 1: No Authorization header or not Bearer scheme
        if (authHeader == null || !authHeader.startsWith("Bearer "))
        {
            log.warn("Missing or invalid Authorization header for protected endpoint: {}", request.getRequestURI());
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing or invalid Authorization header");
            return;
        }

        final String token = authHeader.substring(7);

        // Case 2: Token is invalid (expired, tampered, etc.)
        if (!jwtService.validateToken(token))
        {
            log.warn("Invalid JWT token for protected endpoint: {}", request.getRequestURI());
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid or expired token");
            return;
        }

        // Case 3: Token valid -> extract username and set authentication
        // get username
        final String username = jwtService.extractUsername(token);
        // get entire token payload
        Claims claims = jwtService.extractAllClaims(token);
        // get the list of roles in strings
        List<String> roles = claims.get("roles", List.class);
        // add ROLE_ prefix
        List<GrantedAuthority> authorities = roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .collect(Collectors.toList());
        // create a user object
        UserDetails userDetails = new User(username, "", Collections.emptyList());
        // create authenticated token
        UsernamePasswordAuthenticationToken authenticationToken =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

        // attach request metadata (IP, etc) for auditing
        authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

        // store authentication for this request thread
        SecurityContextHolder.getContext().setAuthentication(authenticationToken);

        // Continue the filter chain with authenticated user
        filterChain.doFilter(request, response);
    }
}