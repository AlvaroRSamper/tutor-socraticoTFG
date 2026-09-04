package es.uma.tfg.tutor_socratico.controlador;

import es.uma.tfg.tutor_socratico.perfil.PerfilAprendizajeServicio;
import es.uma.tfg.tutor_socratico.dto.RespuestaChat;
import es.uma.tfg.tutor_socratico.servicio.ServicioRegistroConsultas;
import es.uma.tfg.tutor_socratico.servicio.ServicioTutor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pruebas de integración web para {@link ControladorTutor} (/api/tutor).
 * Ejercitan la comunicación principal con el tutor (/chat), la generación de repasos, la
 * valoración de consultas y la validación de entrada. Los servicios internos
 * ({@link ServicioTutor}, {@link ServicioRegistroConsultas}, {@link PerfilAprendizajeServicio})
 * se sustituyen por mocks para forzar escenarios de éxito y error sin invocar al LLM.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ControladorTutorTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ServicioTutor servicioTutor;

    @MockitoBean
    private ServicioRegistroConsultas servicioRegistroConsultas;

    @MockitoBean
    private PerfilAprendizajeServicio perfilAprendizajeServicio;

    // ---------------- /chat ----------------

    @Test
    void chatConHistorialValidoDevuelve200() throws Exception {
        when(servicioTutor.consultarTutor(any(), anyString(), anyString()))
                .thenReturn(RespuestaChat.de("¿Qué crees que ocurre aquí?", "CALIBRACION", true, 7L));

        mockMvc.perform(post("/api/tutor/chat")
                        .with(user("12345").roles("ALUMNO"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"historial\":[{\"role\":\"user\",\"content\":\"hola\"}],\"tema\":\"General\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value("¿Qué crees que ocurre aquí?"))
                .andExpect(jsonPath("$.fase").value("CALIBRACION"))
                .andExpect(jsonPath("$.mostrarOpciones").value(true))
                .andExpect(jsonPath("$.consultaId").value(7));
    }

    @Test
    void chatConHistorialVacioDevuelve400() throws Exception {
        // historial @NotEmpty y tema @NotBlank -> MethodArgumentNotValidException -> 400.
        mockMvc.perform(post("/api/tutor/chat")
                        .with(user("12345").roles("ALUMNO"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"historial\":[],\"tema\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").exists());
    }

    @Test
    void chatSinAutenticarDevuelve401() throws Exception {
        mockMvc.perform(post("/api/tutor/chat")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"historial\":[{\"role\":\"user\",\"content\":\"hola\"}],\"tema\":\"General\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ---------------- /repaso ----------------

    @Test
    void generarRepasoDevuelveApuntesMarkdown() throws Exception {
        when(servicioTutor.generarApuntesRepaso(any(), anyString()))
                .thenReturn("# Repaso\n- Punto clave");

        mockMvc.perform(post("/api/tutor/repaso")
                        .with(user("12345").roles("ALUMNO"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"historial\":[{\"role\":\"user\",\"content\":\"resumen\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apuntes").value("# Repaso\n- Punto clave"));
    }

    // ---------------- /valoracion ----------------

    @Test
    void valorarConsultaConIdDevuelveExito() throws Exception {
        when(servicioRegistroConsultas.registrarValoracion(eq(42L), anyInt(), any()))
                .thenReturn(true);

        mockMvc.perform(post("/api/tutor/valoracion")
                        .with(user("12345").roles("ALUMNO"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"consultaId\":42,\"valoracion\":1,\"comentario\":\"Útil\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exito").value(true));
    }

    @Test
    void valorarConsultaSinIdDevuelve400() throws Exception {
        // consultaId es @NotNull: sin él, validación -> 400 (antes provocaba parseo manual frágil).
        mockMvc.perform(post("/api/tutor/valoracion")
                        .with(user("12345").roles("ALUMNO"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"valoracion\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").exists());
    }

    @Test
    void valorarConsultaConIdNoNumericoDevuelve400() throws Exception {
        // Antes: Long.valueOf("abc") -> 500. Ahora: cuerpo ilegible -> 400 controlado.
        mockMvc.perform(post("/api/tutor/valoracion")
                        .with(user("12345").roles("ALUMNO"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"consultaId\":\"abc\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").exists());
    }

    // ---------------- /preferencia ----------------

    @Test
    void registrarPreferenciaValidaDevuelve200() throws Exception {
        mockMvc.perform(post("/api/tutor/preferencia")
                        .with(user("12345").roles("ALUMNO"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"opcion\":\"A\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").exists());
    }

    @Test
    void registrarPreferenciaInvalidaDevuelve400() throws Exception {
        // opcion debe ser 'A' o 'B' (@Pattern) -> 400.
        mockMvc.perform(post("/api/tutor/preferencia")
                        .with(user("12345").roles("ALUMNO"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"opcion\":\"Z\"}"))
                .andExpect(status().isBadRequest());
    }
}
