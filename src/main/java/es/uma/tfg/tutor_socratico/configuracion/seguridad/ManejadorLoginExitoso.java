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

    /** Asignatura por defecto para el login manual (por LTI la sobrescribe la del curso). */
    private static final String ASIGNATURA_POR_DEFECTO = "General";

    /**
     * Cuentas de prueba aisladas en su propia asignatura, para que el profesor pruebe la app
     * en un entorno limpio sin mezclar sus datos con el resto de cuentas de demostración.
     */
    private static final Map<String, String> ASIGNATURA_POR_USUARIO = Map.of(
            "10001", "PRUEBAS",
            "90001", "PRUEBAS"
    );

    private final ObjectMapper objectMapper;

    public ManejadorLoginExitoso(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
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

        String asignatura = ASIGNATURA_POR_USUARIO.getOrDefault(authentication.getName(), ASIGNATURA_POR_DEFECTO);
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
