
package com.ecm.enterprisecontentmanagementbackend.config;

import com.ecm.enterprisecontentmanagementbackend.security.DatabaseUserDetailsService;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

@Configuration
public class SecurityConfig {

    /*
     * WOPI security filter chain
     *
     * Applies only to /wopi/files/**
     *
     * Do not configure the normal OAuth2 JWT resource server here.
     * Collabora may send WOPI tokens in the Authorization header,
     * and the ECM JWT decoder would reject their issuer.
     *
     * WopiController must validate the document-scoped WOPI token
     * for every protected WOPI operation.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain wopiSecurityFilterChain(
            HttpSecurity http
    ) throws Exception {

        return http
                .securityMatcher("/wopi/files/**")

                .csrf(csrf -> csrf.disable())

                .sessionManagement(session ->
                        session.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )

                .authorizeHttpRequests(auth -> auth
                        .anyRequest().permitAll()
                )

                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())

                .build();
    }

    /*
     * Main ECM API security filter chain
     *
     * Uses normal ECM JWT authentication and role-based authorization.
     */
    @Bean
    @Order(2)
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http
    ) throws Exception {

        return http
                .csrf(csrf -> csrf.disable())

                .sessionManagement(session ->
                        session.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )

                .authorizeHttpRequests(auth -> auth

                        // Public health endpoints
                        .requestMatchers(
                                "/api/v1/health",
                                "/actuator/health"
                        ).permitAll()

                        // Public login
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/auth/login"
                        ).permitAll()

                        // Audit logs: EDITOR or ADMIN
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/documents/*/audit-logs"
                        ).hasAnyRole("EDITOR", "ADMIN")

                        // Delete: ADMIN only
                        .requestMatchers(
                                HttpMethod.DELETE,
                                "/api/v1/documents/*"
                        ).hasRole("ADMIN")

                        // Create and upload: EDITOR or ADMIN
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/documents",
                                "/api/v1/documents/upload",
                                "/api/v1/documents/*/versions"
                        ).hasAnyRole("EDITOR", "ADMIN")

                        // Update: EDITOR or ADMIN
                        .requestMatchers(
                                HttpMethod.PUT,
                                "/api/v1/documents/*"
                        ).hasAnyRole("EDITOR", "ADMIN")

                        // Read, search, download, versions
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/documents",
                                "/api/v1/documents/*",
                                "/api/v1/documents/*/download",
                                "/api/v1/documents/*/versions",
                                "/api/v1/documents/*/versions/*/download"
                        ).hasAnyRole("VIEWER", "EDITOR", "ADMIN")

                        // Issue WOPI tokens to authenticated ECM users
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/documents/*/wopi-token"
                        ).hasAnyRole("VIEWER", "EDITOR", "ADMIN")

                        // Reject other document operations
                        .requestMatchers(
                                "/api/v1/documents/**"
                        ).denyAll()

                        // Reject all other endpoints
                        .anyRequest().denyAll()
                )

                .oauth2ResourceServer(oauth2 ->
                        oauth2.jwt(jwt ->
                                jwt.jwtAuthenticationConverter(
                                        jwtAuthenticationConverter()
                                )
                        )
                )

                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())

                .build();
    }

    /*
     * Convert the roles claim from the ECM JWT into
     * Spring Security authorities.
     *
     * Example:
     * roles: ["ROLE_ADMIN"]
     */
    private JwtAuthenticationConverter jwtAuthenticationConverter() {

        JwtGrantedAuthoritiesConverter authoritiesConverter =
                new JwtGrantedAuthoritiesConverter();

        authoritiesConverter.setAuthoritiesClaimName("roles");
        authoritiesConverter.setAuthorityPrefix("");

        JwtAuthenticationConverter converter =
                new JwtAuthenticationConverter();

        converter.setJwtGrantedAuthoritiesConverter(
                authoritiesConverter
        );

        return converter;
    }

    /*
     * Username/password authentication for the login endpoint.
     */
    @Bean
    public AuthenticationManager authenticationManager(
            DatabaseUserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder
    ) {

        DaoAuthenticationProvider provider =
                new DaoAuthenticationProvider(userDetailsService);

        provider.setPasswordEncoder(passwordEncoder);

        return new ProviderManager(provider);
    }
}
