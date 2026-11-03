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
     * - ADMIN y VENDEDOR: pueden listar productos, registrar ventas (POST), abrir/cerrar caja y
     *   solicitar un retiro.
     * - Aprobar o rechazar la petición de un VENDEDOR (retiro o descuento): solo ADMIN.
     * - Alta/baja de productos y carga de stock: solo ADMIN.
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
                // Peticiones de autorización: el VENDEDOR solo consulta cómo quedaron las suyas.
                .requestMatchers(HttpMethod.GET, "/api/autorizaciones/estado").hasAnyRole("ADMIN", "VENDEDOR")
                .requestMatchers("/api/autorizaciones/**").hasRole("ADMIN")
                .requestMatchers("/api/push/**").hasRole("ADMIN")
                .requestMatchers("/api/proveedores/**").hasRole("ADMIN")
                .requestMatchers("/api/gastos/**").hasRole("ADMIN")
                .requestMatchers("/api/compras/**").hasRole("ADMIN")
                .requestMatchers("/api/empleados/**").hasRole("ADMIN")
                // Clientes y Notas de crédito: ADMIN-only por ahora, igual que Proveedores/Compras
                // — Cobros no pide todavía un cliente (eso es la fase de saldo a favor como medio
                // de pago), así que VENDEDOR no necesita tocar ninguno de los dos endpoints.
                .requestMatchers("/api/clientes/**").hasRole("ADMIN")
                .requestMatchers("/api/notas-credito/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/productos/**").hasAnyRole("ADMIN", "VENDEDOR")
                .requestMatchers(HttpMethod.POST, "/api/productos", "/api/productos/cargar-stock").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PATCH, "/api/productos/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/productos/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/ventas").hasAnyRole("ADMIN", "VENDEDOR")
                // Historial de ventas: solo ADMIN, ni global ni por turno (ver nota arriba).
                .requestMatchers(HttpMethod.GET, "/api/ventas").hasRole("ADMIN")
                // Enviar el comprobante de una venta por email: día a día, igual que registrarla.
                // El historial (GET /api/ventas, arriba) sigue siendo lo único exclusivo de ADMIN.
                .requestMatchers(HttpMethod.POST, "/api/ventas/*/enviar-comprobante")
                    .hasAnyRole("ADMIN", "VENDEDOR")
                // Trabajo a domicilio (crear/editar/reabrir un trabajo, ver una venta puntual,
                // descargar su comprobante): a diferencia de Cobros, todo el módulo queda
                // exclusivo de ADMIN — VENDEDOR no lo usa.
                .requestMatchers(HttpMethod.GET, "/api/ventas/*").hasRole("ADMIN")
                // Comprobante: VENDEDOR también, pero solo de sus ventas confirmadas (lo valida el
                // controller, porque depende de la venta y no solo del rol).
                .requestMatchers(HttpMethod.GET, "/api/ventas/*/pdf").hasAnyRole("ADMIN", "VENDEDOR")
                .requestMatchers("/api/ventas/trabajo-domicilio/**").hasRole("ADMIN")
                // Facturación fiscal (ARCA/WSFE): vive únicamente en Consulta de ventas, pantalla
                // ya exclusiva de ADMIN — se mantiene ADMIN-only acá también, es una acción
                // irreversible (emite un comprobante fiscal real). El "/**" es necesario: "*" solo
                // matchea un segmento, así que sin él "/factura/pdf" y "/factura/enviar-email"
                // quedaban sin matcher propio y cualquier rol autenticado los alcanzaba por el
                // "anyRequest().authenticated()" genérico de abajo (bug real, encontrado escribiendo
                // los tests de FacturaFiscalController).
                .requestMatchers("/api/ventas/*/factura", "/api/ventas/*/factura/**").hasRole("ADMIN")
                // Presupuestos: herramienta de venta del día a día (como Cobros), no un reporte
                // administrativo — no afecta stock ni caja, así que no sigue la regla de abajo
                // de que todo "/api/reportes/**" es exclusivo de ADMIN.
                .requestMatchers("/api/presupuestos/**").hasAnyRole("ADMIN", "VENDEDOR")
                // Abrir/cerrar caja y solicitar un retiro: operación del día a día,
                // VENDEDOR también puede. Ver resúmenes sigue exclusivo de ADMIN (matcher general
                // de abajo).
                .requestMatchers(HttpMethod.POST, "/api/caja/abrir", "/api/caja/cerrar",
                        "/api/caja/retiro/solicitar")
                    .hasAnyRole("ADMIN", "VENDEDOR")
                .requestMatchers("/api/caja/**").hasRole("ADMIN")
                .requestMatchers("/api/reportes/**").hasRole("ADMIN")
                .anyRequest().authenticated())
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
