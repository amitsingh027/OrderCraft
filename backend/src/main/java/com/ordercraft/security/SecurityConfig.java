package com.ordercraft.security;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    static void error(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write("{\"message\":\"" + message + "\"}");
    }

    @Bean
    SecurityFilterChain security(HttpSecurity http) throws Exception {
        return http
            .csrf(Customizer.withDefaults())
            .requestCache(cache -> cache.disable())
            .authorizeHttpRequests(auth ->
                auth
                    .requestMatchers("/api/auth/csrf", "/api/auth/login")
                    .permitAll()
                    .requestMatchers("/api/admin/**")
                    .hasRole("ADMIN")
                    .requestMatchers(HttpMethod.POST, "/api/orders")
                    .hasAnyRole("ADMIN", "SALES")
                    .requestMatchers(
                        HttpMethod.POST,
                        "/api/orders/*/bom",
                        "/api/orders/*/po",
                        "/api/orders/*/advance"
                    )
                    .hasAnyRole("ADMIN", "PRODUCTION")
                    .requestMatchers(
                        HttpMethod.POST,
                        "/api/orders/*/invoice",
                        "/api/orders/*/payment"
                    )
                    .hasAnyRole("ADMIN", "ACCOUNTS")
                    .requestMatchers(HttpMethod.GET, "/api/**")
                    .authenticated()
                    .requestMatchers("/api/auth/logout")
                    .authenticated()
                    .requestMatchers("/api/**")
                    .denyAll()
                    .anyRequest()
                    .permitAll()
            )
            .formLogin(login ->
                login
                    .loginProcessingUrl("/api/auth/login")
                    .successHandler((request, response, authentication) -> {
                        response.setContentType("application/json");
                        response.getWriter().write("{\"authenticated\":true}");
                    })
                    .failureHandler((request, response, exception) ->
                        error(response, 401, "Incorrect username or password.")
                    )
            )
            .logout(logout ->
                logout
                    .logoutUrl("/api/auth/logout")
                    .deleteCookies("JSESSIONID")
                    .logoutSuccessHandler((request, response, authentication) ->
                        response.setStatus(204)
                    )
            )
            .exceptionHandling(errors ->
                errors
                    .authenticationEntryPoint((request, response, exception) ->
                        error(response, 401, "Please sign in to continue.")
                    )
                    .accessDeniedHandler((request, response, exception) ->
                        error(
                            response,
                            403,
                            "You do not have permission, or your session token has expired. Refresh and try again."
                        )
                    )
            )
            .build();
    }
}
