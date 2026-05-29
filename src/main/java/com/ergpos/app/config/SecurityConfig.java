package com.ergpos.app.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.ergpos.app.security.JwtAuthenticationFilter;
import com.ergpos.app.repositories.UsuarioRepository;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final List<String> allowedOriginPatterns;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter,
            @Value("${app.cors.allowed-origin-patterns}") List<String> allowedOriginPatterns) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.allowedOriginPatterns = allowedOriginPatterns;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            .httpBasic(httpBasic -> httpBasic.disable())
            .formLogin(formLogin -> formLogin.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/api/auth/login", "/error").permitAll()
                .requestMatchers("/api/auth/register").hasRole("ADMIN")
                .requestMatchers("/api/asistente/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/clientes/activos").hasAnyRole("ADMIN", "VENDEDOR", "TECNICO")
                .requestMatchers(HttpMethod.GET, "/api/configuracion").authenticated()
                .requestMatchers("/api/configuracion/**").hasRole("ADMIN")
                .requestMatchers("/api/auditoria/**", "/api/reportes/**", "/api/usuarios/**", "/api/roles/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/devoluciones-garantias/**").hasAnyRole("ADMIN", "VENDEDOR", "TECNICO")
                .requestMatchers(HttpMethod.DELETE, "/api/devoluciones-garantias/**").hasRole("ADMIN")
                .requestMatchers("/api/devoluciones-garantias/**").hasAnyRole("ADMIN", "VENDEDOR", "TECNICO")
                .requestMatchers("/api/compras/**", "/api/proveedores/**", "/api/movimientos-inventario/**", "/api/kardex/**").hasAnyRole("ADMIN", "INVENTARIO")
                .requestMatchers("/api/ventas/**", "/api/clientes/**", "/api/facturas-venta/**").hasAnyRole("ADMIN", "VENDEDOR")
                .requestMatchers(HttpMethod.POST, "/api/entregas/*/confirmar", "/api/entregas/*/pagos").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PATCH, "/api/entregas/*/anticipo").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/entregas").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/entregas/*").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/entregas/*").hasRole("ADMIN")
                .requestMatchers("/api/entregas/**").hasAnyRole("ADMIN", "TECNICO")
                .requestMatchers(HttpMethod.GET, "/api/productos/**", "/api/categorias/**", "/api/dashboard/**").authenticated()
                .requestMatchers("/api/productos/**", "/api/categorias/**").hasAnyRole("ADMIN", "INVENTARIO")
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService(UsuarioRepository usuarioRepository) {
        return email -> usuarioRepository.findByEmail(email)
                .filter(usuario -> Boolean.TRUE.equals(usuario.getActivo()))
                .map(usuario -> User.withUsername(usuario.getEmail())
                        .password(usuario.getPassword())
                        .authorities("ROLE_" + usuario.getRol().getNombre())
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado: " + email));
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(allowedOriginPatterns);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
