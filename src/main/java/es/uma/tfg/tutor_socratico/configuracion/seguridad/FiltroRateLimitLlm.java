package es.uma.tfg.tutor_socratico.configuracion.seguridad;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


@Slf4j
public class FiltroRateLimitLlm extends OncePerRequestFilter {

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final int capacidad;
    private final int minutos;
    private final ObjectMapper objectMapper;

    public FiltroRateLimitLlm(int capacidad, int minutos, ObjectMapper objectMapper) {
        this.capacidad = capacidad;
        this.minutos = minutos;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String ruta = rutaRelativa(request);
        return !(ruta.startsWith("/api/tutor/") || ruta.startsWith("/api/reto/"));
    }

    
    private String rutaRelativa(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contexto = request.getContextPath();
        return (contexto != null && !contexto.isEmpty() && uri.startsWith(contexto))
                ? uri.substring(contexto.length())
                : uri;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Bucket bucket = buckets.computeIfAbsent(clave(request), k -> nuevoBucket());
        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
            return;
        }
        log.warn("Rate limit superado para '{}' en {}", clave(request), rutaRelativa(request));
        response.setStatus(429); 
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(),
                Map.of("mensaje", "Has realizado demasiadas peticiones en poco tiempo. Espera un momento e inténtalo de nuevo."));
    }

    private String clave(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
            return "user:" + auth.getName();
        }
        return "ip:" + request.getRemoteAddr();
    }

    private Bucket nuevoBucket() {
        Bandwidth limite = Bandwidth.classic(capacidad, Refill.greedy(capacidad, Duration.ofMinutes(minutos)));
        return Bucket.builder().addLimit(limite).build();
    }
}
