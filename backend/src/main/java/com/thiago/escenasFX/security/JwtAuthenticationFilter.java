package com.thiago.escenasFX.security;

import java.io.IOException;
import java.util.List;

import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.thiago.escenasFX.repository.EmpleadoRepository;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final EmpleadoRepository empleadoRepo;

    public JwtAuthenticationFilter(JwtService jwtService, EmpleadoRepository empleadoRepo) {
        this.jwtService = jwtService;
        this.empleadoRepo = empleadoRepo;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                Claims claims = jwtService.parseClaims(token);
                String usuario = claims.getSubject();
                String rol = claims.get("rol", String.class);

                // Revalida "activo" contra la base en cada request en vez de confiar ciegamente en
                // el rol firmado en el token: un empleado dado de baja (o cuyo token se filtró)
                // seguía teniendo acceso completo con su JWT viejo hasta que expirara solo (hasta
                // 8hs) — el login ya bloqueaba altas nuevas, pero un token ya emitido no se
                // revocaba. Costo de una consulta extra por request, aceptable a esta escala.
                boolean activo = usuario != null
                    && empleadoRepo.findByUsuario(usuario).map(e -> e.isActivo()).orElse(false);

                if (activo && rol != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                    var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + rol));
                    var authToken = new UsernamePasswordAuthenticationToken(usuario, null, authorities);
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            } catch (JwtException | IllegalArgumentException ex) {
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }
}
