package com.thiago.escenasFX.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.thiago.escenasFX.security.JwtAuthenticationFilter;
import com.thiago.escenasFX.security.RestAccessDeniedHandler;
import com.thiago.escenasFX.security.RestAuthenticationEntryPoint;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
            RestAuthenticationEntryPoint authenticationEntryPoint, RestAccessDeniedHandler accessDeniedHandler) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * JWT stateless por roles (ADMIN / VENDEDOR).
     * - ADMIN: unico rol que ve ventas (GET), gestiona caja (abrir/cerrar/retiro) y accede
     *   a reportes.
     * - ADMIN y VENDEDOR: pueden listar productos y registrar ventas (POST).
     * - Confirmar un descuento de venta requiere ser ADMIN (el OTP se le envía a él por email).
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(e -> e
                .authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/productos/**").hasAnyRole("ADMIN", "VENDEDOR")
                .requestMatchers(HttpMethod.POST, "/api/ventas").hasAnyRole("ADMIN", "VENDEDOR")
                .requestMatchers(HttpMethod.GET, "/api/ventas").hasRole("ADMIN")
                .requestMatchers("/api/ventas/descuento/confirmar").hasRole("ADMIN")
                .requestMatchers("/api/caja/**").hasRole("ADMIN")
                .requestMatchers("/api/reportes/**").hasRole("ADMIN")
                .anyRequest().authenticated())
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
