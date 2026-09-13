package es.uma.tfg.tutor_socratico.configuracion.seguridad;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

@Component
public class ManejadorLoginExitoso implements AuthenticationSuccessHandler {

    private final ObjectMapper objectMapper;
    private final RegistroUsuarios registroUsuarios;

    public ManejadorLoginExitoso(ObjectMapper objectMapper, RegistroUsuarios registroUsuarios) {
        this.objectMapper = objectMapper;
        this.registroUsuarios = registroUsuarios;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                         Authentication authentication) throws IOException {

        CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (csrfToken != null) {
            csrfToken.getToken();
        }

        String rol = authentication.getAuthorities().stream()
                .map(a -> a.getAuthority().replaceFirst("^ROLE_", ""))
                .findFirst()
                .orElse("ALUMNO");

        String asignatura = registroUsuarios.asignaturaDe(authentication.getName());
        request.getSession().setAttribute("asignatura_id", asignatura);

        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), Map.of(
                "mensaje", "Login correcto",
                "usuario", authentication.getName(),
                "rol", rol));
    }
}
