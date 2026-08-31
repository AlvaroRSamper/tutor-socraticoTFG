package es.uma.tfg.tutor_socratico.controlador;

import es.uma.tfg.tutor_socratico.servicio.ServicioEstancamiento;
import es.uma.tfg.tutor_socratico.servicio.ServicioIngesta;
import es.uma.tfg.tutor_socratico.servicio.ServicioTutor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pruebas de integración web para {@link ControladorProfesor} (/api/profesor).
 * Verifican que los endpoints están reservados al rol PROFESOR (403 para ALUMNO, 401 sin
 * autenticar), que la configuración de asignatura delega en {@link ServicioIngesta} y que el
 * informe se descarga como CSV. Los servicios de negocio se sustituyen por mocks; los
 * repositorios reales operan sobre la base H2 en memoria.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ControladorProfesorTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ServicioIngesta servicioIngesta;

    @MockitoBean
    private ServicioTutor servicioTutor;

    @MockitoBean
    private ServicioEstancamiento servicioEstancamiento;

    // ---------------- Seguridad por rol ----------------

    @Test
    void alumnoNoPuedeAccederAlResumenDelProfesor() throws Exception {
        mockMvc.perform(get("/api/profesor/resumen").with(user("12345").roles("ALUMNO")))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    void alumnoNoPuedeConfigurarAsignatura() throws Exception {
        mockMvc.perform(post("/api/profesor/asignatura/configurar")
                        .param("systemPrompt", "Eres un tutor")
                        .with(user("12345").roles("ALUMNO"))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void peticionSinAutenticarDevuelve401() throws Exception {
        mockMvc.perform(get("/api/profesor/resumen"))
                .andExpect(status().isUnauthorized());
    }

    // ---------------- Configuración de asignatura ----------------

    @Test
    void profesorConfiguraAsignaturaCorrectamente() throws Exception {
        when(servicioIngesta.configurarAsignatura(anyString(), any(), anyString(), any(), any(), any()))
                .thenReturn(3);

        mockMvc.perform(post("/api/profesor/asignatura/configurar")
                        .param("titulo", "POO")
                        .param("systemPrompt", "Eres un tutor socrático de POO")
                        .param("colorTema", "#58a6ff")
                        .param("sensibilidad", "5")
                        .with(user("99991").roles("PROFESOR"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exito").value(true))
                .andExpect(jsonPath("$.documentosProcesados").value(3));
    }

    @Test
    void configurarSinSystemPromptDevuelve400() throws Exception {
        // systemPrompt es obligatorio: al faltar, MissingServletRequestParameterException se mapea
        // ahora a 400 (antes el handler genérico lo convertía en un 500 engañoso).
        mockMvc.perform(post("/api/profesor/asignatura/configurar")
                        .param("titulo", "POO")
                        .with(user("99991").roles("PROFESOR"))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").exists());
    }

    // ---------------- Informe CSV ----------------

    @Test
    void profesorDescargaInformeCsv() throws Exception {
        // El informe se genera con StreamingResponseBody (async): hay que despachar la parte asíncrona.
        MvcResult resultado = mockMvc.perform(get("/api/profesor/informe").with(user("99991").roles("PROFESOR")))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(resultado))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(new MediaType("text", "csv")))
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(content().string(containsString("id,fechaHora,alumno")));
    }

    @Test
    void alumnoNoPuedeDescargarInforme() throws Exception {
        mockMvc.perform(get("/api/profesor/informe").with(user("12345").roles("ALUMNO")))
                .andExpect(status().isForbidden());
    }
}
