package es.uma.tfg.tutor_socratico.controlador;

import es.uma.tfg.tutor_socratico.servicio.ServicioLti;
import es.uma.tfg.tutor_socratico.servicio.ServicioLti.DatosLanzamiento;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pruebas de integración web para {@link LtiLaunchController} (flujo de autenticación LTI).
 * Verifican la redirección inicial de {@code /lti/login} hacia el proveedor OpenID y el callback
 * {@code /lti/launch}, donde se valida el estado de sesión y se establece el contexto de seguridad
 * a partir del token. El {@link ServicioLti} se sustituye por un mock para no verificar firmas JWT
 * reales. Se inyectan propiedades LTI de prueba porque el fichero de test no las define.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "lti.issuer=https://moodle.test",
        "lti.client-id=cliente-test",
        "lti.jwks-uri=https://moodle.test/.well-known/jwks.json",
        "lti.auth-login-url=https://moodle.test/auth"
})
class LtiLaunchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ServicioLti servicioLti;

    // ---------------- /lti/login ----------------

    @Test
    void loginRedirigeAlProveedorConLosParametrosOpenId() throws Exception {
        mockMvc.perform(get("/lti/login")
                        .param("login_hint", "usuario-123")
                        .param("target_link_uri", "https://app.test/lti/launch"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("https://moodle.test/auth")))
                .andExpect(header().string("Location", containsString("scope=openid")))
                .andExpect(header().string("Location", containsString("response_type=id_token")))
                .andExpect(header().string("Location", containsString("client_id=cliente-test")))
                .andExpect(header().string("Location", containsString("login_hint=usuario-123")));
    }

    // ---------------- /lti/launch ----------------

    @Test
    void launchConTokenValidoDeProfesorRedirigeAlPanelDocente() throws Exception {
        when(servicioLti.procesarLanzamiento(anyString(), any()))
                .thenReturn(new DatosLanzamiento("99991", "PROFESOR", "MAT101"));

        mockMvc.perform(post("/lti/launch")
                        .session(sesionConEstado("estado-1", "nonce-1"))
                        .param("id_token", "token-jwt-simulado")
                        .param("state", "estado-1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/profesor.html"));
    }

    @Test
    void launchConTokenValidoDeAlumnoRedirigeAlIndex() throws Exception {
        when(servicioLti.procesarLanzamiento(anyString(), any()))
                .thenReturn(new DatosLanzamiento("12345", "ALUMNO", "MAT101"));

        mockMvc.perform(post("/lti/launch")
                        .session(sesionConEstado("estado-1", "nonce-1"))
                        .param("id_token", "token-jwt-simulado")
                        .param("state", "estado-1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/index.html"));
    }

    @Test
    void launchSinStateEnSesionDevuelve401() throws Exception {
        // Un POST directo a /lti/launch (sin pasar por /lti/login) no tiene state en sesión → 401.
        mockMvc.perform(post("/lti/launch")
                        .param("id_token", "token-jwt-simulado")
                        .param("state", "cualquiera"))
                .andExpect(status().isUnauthorized());
    }

    private MockHttpSession sesionConEstado(String state, String nonce) {
        MockHttpSession sesion = new MockHttpSession();
        sesion.setAttribute("lti_state", state);
        sesion.setAttribute("lti_nonce", nonce);
        return sesion;
    }

    @Test
    void launchConEstadoNoCoincidenteDevuelve401() throws Exception {
        MockHttpSession sesion = new MockHttpSession();
        sesion.setAttribute("lti_state", "estado-esperado");

        mockMvc.perform(post("/lti/launch")
                        .session(sesion)
                        .param("id_token", "token-jwt-simulado")
                        .param("state", "estado-distinto"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void launchConTokenInvalidoDevuelve401() throws Exception {
        when(servicioLti.procesarLanzamiento(anyString(), any()))
                .thenThrow(new BadCredentialsException("Token no válido"));

        mockMvc.perform(post("/lti/launch")
                        .param("id_token", "token-corrupto"))
                .andExpect(status().isUnauthorized());
    }
}
