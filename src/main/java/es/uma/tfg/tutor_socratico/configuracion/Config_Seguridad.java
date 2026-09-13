package es.uma.tfg.tutor_socratico.configuracion;

import es.uma.tfg.tutor_socratico.configuracion.seguridad.CsrfCookieFilter;
import es.uma.tfg.tutor_socratico.configuracion.seguridad.ManejadorAccesoDenegado;
import es.uma.tfg.tutor_socratico.configuracion.seguridad.ManejadorEntryPointNoAutenticado;
import es.uma.tfg.tutor_socratico.configuracion.seguridad.ManejadorLoginExitoso;
import es.uma.tfg.tutor_socratico.configuracion.seguridad.ManejadorLoginFallido;
import es.uma.tfg.tutor_socratico.configuracion.seguridad.ManejadorLogoutExitoso;
import es.uma.tfg.tutor_socratico.configuracion.seguridad.RegistroUsuarios;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.beans.factory.annotation.Value;
import com.fasterxml.jackson.databind.ObjectMapper;
import es.uma.tfg.tutor_socratico.configuracion.seguridad.FiltroRateLimitLlm;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;


@Configuration
@EnableWebSecurity
@EnableConfigurationProperties({PropiedadesUsuarios.class, PropiedadesLti.class})
public class Config_Seguridad {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService(RegistroUsuarios registroUsuarios) {
        return new InMemoryUserDetailsManager(registroUsuarios.usuarios());
    }


    @Bean
    public CookieCsrfTokenRepository csrfTokenRepository() {
        return CookieCsrfTokenRepository.withHttpOnlyFalse();
    }


    
    @Bean
    public SecurityFilterChain filtroSeguridad(HttpSecurity http,
                                                CookieCsrfTokenRepository csrfTokenRepository,
                                                ManejadorLoginExitoso manejadorLoginExitoso,
                                                ManejadorLoginFallido manejadorLoginFallido,
                                                ManejadorEntryPointNoAutenticado manejadorEntryPoint,
                                                ManejadorLogoutExitoso manejadorLogoutExitoso,
                                                ManejadorAccesoDenegado manejadorAccesoDenegado,
                                                ObjectMapper objectMapper,
                                                @Value("${tutor.security.frame-ancestors:'self'}") String frameAncestors,
                                                @Value("${tutor.rate-limit.capacidad}") int rateLimitCapacidad,
                                                @Value("${tutor.rate-limit.minutos}") int rateLimitMinutos) throws Exception {
        
        
        
        
        String csp = "default-src 'self'; "
                + "script-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net https://cdnjs.cloudflare.com; "
                + "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; "
                + "font-src 'self' https://fonts.gstatic.com; "
                + "img-src 'self' data:; "
                + "connect-src 'self'; "
                + "object-src 'none'; "
                + "base-uri 'self'; "
                + "form-action 'self'; "
                + "frame-ancestors " + frameAncestors + ";";
        http
                .headers(headers -> headers
                        
                        .frameOptions(frame -> frame.disable())
                        .contentSecurityPolicy(policy -> policy.policyDirectives(csp))
                        .referrerPolicy(rp -> rp.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/index.html", "/profesor.html", "/css/**", "/js/**",
                                "/logo-uma.png", "/login", "/logout").permitAll()
                        .requestMatchers("/lti/**").permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/api/tutor/**").hasAnyRole("ALUMNO", "PROFESOR")
                        .requestMatchers("/api/reto/profesor/**").hasRole("PROFESOR")
                        .requestMatchers("/api/reto/**").hasAnyRole("ALUMNO", "PROFESOR")
                        .requestMatchers("/api/alumno/**").hasAnyRole("ALUMNO", "PROFESOR")
                        .requestMatchers("/api/profesor/**").hasRole("PROFESOR")
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginProcessingUrl("/login")
                        .successHandler(manejadorLoginExitoso)
                        .failureHandler(manejadorLoginFallido)
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessHandler(manejadorLogoutExitoso))
                .csrf(csrf -> csrf
                        .ignoringRequestMatchers("/lti/**")
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                        .csrfTokenRepository(csrfTokenRepository))
                .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
                .addFilterAfter(new FiltroRateLimitLlm(rateLimitCapacidad, rateLimitMinutos, objectMapper),
                        AuthorizationFilter.class)
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(manejadorEntryPoint)
                        .accessDeniedHandler(manejadorAccesoDenegado));
        return http.build();
    }
}
