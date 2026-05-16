package com.filesync.server.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter
{
    private final JwtService jwtService;

    // list of public paths that should skip JWT authentication
    private final List<String> publicPaths = List.of(
            "/api/auth/login",
            "/api/users/register",
            "/api/users/forgot-password",
            "/api/users/reset-password",
            "/health",
            "/actuator/prometheus",
            "/monitoring",
            "/monitoring/",
            "/debug/"
    );

    public JwtAuthenticationFilter(JwtService jwtService)
    {
        this.jwtService = jwtService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return publicPaths.stream().anyMatch(path::startsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException
    {
        final String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer "))
        {
            filterChain.doFilter(request, response);
            return;
        }
        // remove "Bearer " prefix
        final String token = authHeader.substring(7);

        // if invalid token, skip
        if (!jwtService.validateToken(token))
        {
            filterChain.doFilter(request, response);
            return;
        }

        final String username = jwtService.extractUsername(token);

        // create a plain UserDetails
        UserDetails userDetails = new User(username, "", Collections.emptyList());

        // create an authentication token
        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(userDetails, null,
                userDetails.getAuthorities());

        authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

        // set the authentication in the security context
        SecurityContextHolder.getContext().setAuthentication(authenticationToken);

        filterChain.doFilter(request, response);
    }
}