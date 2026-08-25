package com.thiago.escenasFX.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
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
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.thiago.escenasFX.security.JwtAuthenticationFilter;
import com.thiago.escenasFX.security.RestAccessDeniedHandler;
import com.thiago.escenasFX.security.RestAuthenticationEntryPoint;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;

    @Value("${app.cors.allowed-origins:http://localhost:5173,http://localhost:3000}")
    private List<String> allowedOrigins;

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
     * Origenes permitidos via app.cors.allowed-origins (CSV), default: puertos típicos de
     * Vite/CRA en desarrollo local. En producción, sobreescribir con el dominio real del
     * frontend.
     */
   @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        
        // 1. Poné el dominio fijo para descartar errores de lectura de variables
config.setAllowedOrigins(allowedOrigins);        
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        
        // 2. CLAVE: Permitir todas las cabeceras (asterisco)
        config.setAllowedHeaders(List.of("*")); 
        
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    /**
     * JWT stateless por roles (ADMIN / VENDEDOR).
     * - ADMIN: unico rol que ve ventas (GET), resúmenes de caja y accede a reportes.
     * - ADMIN y VENDEDOR: pueden listar productos, registrar ventas (POST), abrir/cerrar caja,
     *   solicitar un retiro, y confirmar un descuento de venta o un retiro de caja.
     * - Alta/baja de productos y carga de stock: solo ADMIN.
     *
     * Flujo de autorización real (2026-07-30, aclarado por el dueño del negocio): el OTP de un
     * descuento o un retiro le llega por email solo al ADMIN — eso es lo que evita que el
     * VENDEDOR se autoconfirme. Pero quien está frente a la caja (a menudo el VENDEDOR) es quien
     * tiene que terminar la operación: el ADMIN le pasa el código (llamado, WhatsApp, etc.) y el
     * VENDEDOR lo escribe para cerrarla. Por eso confirmar NO puede quedar exclusivo de ADMIN en
     * el backend — el control de seguridad está en quién recibe el código por email, no en qué
     * rol aprieta "Confirmar".
     *
     * Decisión de negocio (confirmada por el dueño del negocio): el VENDEDOR no debe poder ver
     * el historial de ventas ni el arqueo de caja bajo ninguna forma, ni siquiera el de su propio
     * turno. Por eso "GET /api/caja/resumen-dia", "GET /api/caja/resumen" y "GET /api/ventas"
     * quedan exclusivamente para ADMIN — no existe, ni debe agregarse, un endpoint de "mis
     * ventas"/"resumen de mi turno" para VENDEDOR.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(e -> e
                .authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/marcas").hasAnyRole("ADMIN", "VENDEDOR")
                .requestMatchers(HttpMethod.GET, "/api/caja/sesion-abierta").hasAnyRole("ADMIN", "VENDEDOR")
                // Gate diario de cotización (dolarización): se consulta/dispara para cualquier rol
                // apenas loguea — nadie puede operar sin la cotización del día. Cargarla a mano
                // (cuando fallan las dos APIs) queda exclusivo de ADMIN.
                .requestMatchers(HttpMethod.GET, "/api/cotizacion/actual").hasAnyRole("ADMIN", "VENDEDOR")
                .requestMatchers(HttpMethod.POST, "/api/cotizacion/cargar").hasAnyRole("ADMIN", "VENDEDOR")
                .requestMatchers(HttpMethod.POST, "/api/cotizacion/manual").hasRole("ADMIN")
                .requestMatchers("/api/proveedores/**").hasRole("ADMIN")
                .requestMatchers("/api/gastos/**").hasRole("ADMIN")
                .requestMatchers("/api/compras/**").hasRole("ADMIN")
                .requestMatchers("/api/empleados/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/productos/**").hasAnyRole("ADMIN", "VENDEDOR")
                .requestMatchers(HttpMethod.POST, "/api/productos", "/api/productos/cargar-stock").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PATCH, "/api/productos/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/productos/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/ventas").hasAnyRole("ADMIN", "VENDEDOR")
                // Historial de ventas: solo ADMIN, ni global ni por turno (ver nota arriba).
                .requestMatchers(HttpMethod.GET, "/api/ventas").hasRole("ADMIN")
                // Confirmar el descuento: cualquiera de los dos roles (ver nota arriba sobre el
                // flujo real de autorización — el control está en quién recibe el OTP por email).
                .requestMatchers("/api/ventas/descuento/confirmar").hasAnyRole("ADMIN", "VENDEDOR")
                // Enviar el comprobante de una venta por email: día a día, igual que registrarla.
                // El historial (GET /api/ventas, arriba) sigue siendo lo único exclusivo de ADMIN.
                .requestMatchers(HttpMethod.POST, "/api/ventas/*/enviar-comprobante")
                    .hasAnyRole("ADMIN", "VENDEDOR")
                // Trabajo a domicilio (crear/editar/reabrir un trabajo, ver una venta puntual,
                // descargar su comprobante): a diferencia de Cobros, todo el módulo queda
                // exclusivo de ADMIN — VENDEDOR no lo usa.
                .requestMatchers(HttpMethod.GET, "/api/ventas/*").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/ventas/*/pdf").hasRole("ADMIN")
                .requestMatchers("/api/ventas/trabajo-domicilio/**").hasRole("ADMIN")
                // Facturación fiscal (ARCA/WSFE): vive únicamente en Consulta de ventas, pantalla
                // ya exclusiva de ADMIN — se mantiene ADMIN-only acá también, es una acción
                // irreversible (emite un comprobante fiscal real).
                .requestMatchers("/api/ventas/*/factura").hasRole("ADMIN")
                // Presupuestos: herramienta de venta del día a día (como Cobros), no un reporte
                // administrativo — no afecta stock ni caja, así que no sigue la regla de abajo
                // de que todo "/api/reportes/**" es exclusivo de ADMIN.
                .requestMatchers("/api/presupuestos/**").hasAnyRole("ADMIN", "VENDEDOR")
                // Abrir/cerrar caja, solicitar y confirmar un retiro: operación del día a día,
                // VENDEDOR también puede. Ver resúmenes sigue exclusivo de ADMIN (matcher general
                // de abajo).
                .requestMatchers(HttpMethod.POST, "/api/caja/abrir", "/api/caja/cerrar",
                        "/api/caja/retiro/solicitar", "/api/caja/retiro/confirmar")
                    .hasAnyRole("ADMIN", "VENDEDOR")
                .requestMatchers("/api/caja/**").hasRole("ADMIN")
                .requestMatchers("/api/reportes/**").hasRole("ADMIN")
                .anyRequest().authenticated())
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
