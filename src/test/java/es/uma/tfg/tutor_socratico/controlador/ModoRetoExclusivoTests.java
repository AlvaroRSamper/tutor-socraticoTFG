package es.uma.tfg.tutor_socratico.controlador;

import es.uma.tfg.tutor_socratico.dto.RespuestaChat;
import es.uma.tfg.tutor_socratico.dto.RespuestaEstadoReto;
import es.uma.tfg.tutor_socratico.persistencia.Asignatura;
import es.uma.tfg.tutor_socratico.persistencia.AsignaturaRepositorio;
import es.uma.tfg.tutor_socratico.servicio.ServicioReto;
import es.uma.tfg.tutor_socratico.servicio.ServicioTutor;
import es.uma.tfg.tutor_socratico.servicio.ServicioValidacionApuntes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El "modo reto exclusivo" no puede ser solo un ocultar en el navegador: con el interruptor
 * activado el servidor rechaza las funcionalidades que el profesor ha apagado, deja pasar los
 * retos propuestos y lo vuelve a permitir todo al desactivarlo.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ModoRetoExclusivoTests {

    private static final String ASIGNATURA = "General";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AsignaturaRepositorio asignaturaRepositorio;

    @MockitoBean
    private ServicioTutor servicioTutor;

    @MockitoBean
    private ServicioReto servicioReto;

    @MockitoBean
    private ServicioValidacionApuntes servicioValidacion;

    @AfterEach
    void limpiar() {
        asignaturaRepositorio.deleteById(ASIGNATURA);
    }

    private void modoReto(boolean activo) {
        asignaturaRepositorio.save(Asignatura.builder()
                .asignaturaId(ASIGNATURA)
                .modoRetoExclusivo(activo)
                .build());
    }

    @Test
    void conModoRetoActivoElChatLibreDevuelve403() throws Exception {
        modoReto(true);

        mockMvc.perform(post("/api/tutor/chat")
                        .with(user("12345").roles("ALUMNO"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"historial\":[{\"role\":\"user\",\"content\":\"hola\"}],\"tema\":\"General\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").exists());
    }

    @Test
    void conModoRetoDesactivadoElChatLibreSigueFuncionando() throws Exception {
        modoReto(false);
        when(servicioTutor.consultarTutor(any(), anyString(), anyString()))
                .thenReturn(RespuestaChat.de("Pregunta socrática", "CALIBRACION", true, 1L));

        mockMvc.perform(post("/api/tutor/chat")
                        .with(user("12345").roles("ALUMNO"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"historial\":[{\"role\":\"user\",\"content\":\"hola\"}],\"tema\":\"General\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void conModoRetoActivoElRepasoYElValidadorDevuelven403() throws Exception {
        modoReto(true);

        mockMvc.perform(post("/api/tutor/repaso")
                        .with(user("12345").roles("ALUMNO"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"historial\":[{\"role\":\"user\",\"content\":\"hola\"}]}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/alumno/apuntes/validar")
                        .with(user("12345").roles("ALUMNO"))
                        .with(csrf())
                        .param("texto", "mis apuntes"))
                .andExpect(status().isForbidden());
    }

    @Test
    void conModoRetoActivoNoSePuedenCrearEjerciciosPropiosNiTests() throws Exception {
        modoReto(true);

        mockMvc.perform(post("/api/reto/crear-ia")
                        .with(user("12345").roles("ALUMNO"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tema\":\"Herencia\",\"dificultad\":\"media\",\"lenguaje\":\"Java\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/reto/test")
                        .with(user("12345").roles("ALUMNO"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tema\":\"Herencia\",\"dificultad\":\"media\",\"numPreguntas\":5}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void conModoRetoActivoSoloSePuedeIniciarUnRetoPropuesto() throws Exception {
        modoReto(true);
        when(servicioReto.esPropuestoPublicado(anyLong(), anyString())).thenReturn(false);

        mockMvc.perform(post("/api/reto/iniciar")
                        .with(user("12345").roles("ALUMNO"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ejercicioId\":1}"))
                .andExpect(status().isForbidden());

        when(servicioReto.esPropuestoPublicado(anyLong(), anyString())).thenReturn(true);
        when(servicioReto.iniciarResolucion(anyLong(), anyString(), anyString(), anyBoolean()))
                .thenReturn(new RespuestaEstadoReto(5L, 1L, "Reto", "Enunciado", "java",
                        100, 100, null, false, null, List.of(), 0, false));

        mockMvc.perform(post("/api/reto/iniciar")
                        .with(user("12345").roles("ALUMNO"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ejercicioId\":1}"))
                .andExpect(status().isOk());
    }

    @Test
    void losRetosPropuestosSiguenDisponiblesConElModoActivo() throws Exception {
        modoReto(true);
        when(servicioReto.listarPropuestos(anyString(), anyString())).thenReturn(List.of());

        mockMvc.perform(get("/api/reto/propuestos")
                        .with(user("12345").roles("ALUMNO")))
                .andExpect(status().isOk());
    }

    @Test
    void elAlumnoRecibeElEstadoDelModoRetoAlCargarLosTemas() throws Exception {
        modoReto(true);

        mockMvc.perform(get("/api/tutor/temas").with(user("12345").roles("ALUMNO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modoRetoExclusivo").value(true));
    }

    @Test
    void elProfesorActivaYDesactivaElModoReto() throws Exception {
        mockMvc.perform(post("/api/profesor/modo-reto")
                        .with(user("99991").roles("PROFESOR"))
                        .with(csrf())
                        .param("activo", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(true));

        mockMvc.perform(get("/api/profesor/modo-reto").with(user("99991").roles("PROFESOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(true));

        mockMvc.perform(post("/api/profesor/modo-reto")
                        .with(user("99991").roles("PROFESOR"))
                        .with(csrf())
                        .param("activo", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(false));
    }

    @Test
    void guardarLaConfiguracionDeLaAsignaturaNoApagaElModoReto() throws Exception {
        modoReto(true);

        mockMvc.perform(post("/api/profesor/asignatura/configurar")
                        .with(user("99991").roles("PROFESOR"))
                        .with(csrf())
                        .param("systemPrompt", "Eres un tutor socratico.")
                        .param("titulo", "Programacion Orientada a Objetos"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/profesor/modo-reto").with(user("99991").roles("PROFESOR")))
                .andExpect(jsonPath("$.activo").value(true));
    }

    @Test
    void unaAsignaturaSinValorGuardadoCuentaComoModoRetoApagado() throws Exception {
        asignaturaRepositorio.save(Asignatura.builder().asignaturaId(ASIGNATURA).build());

        mockMvc.perform(get("/api/profesor/modo-reto").with(user("99991").roles("PROFESOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(false));

        mockMvc.perform(get("/api/tutor/temas").with(user("12345").roles("ALUMNO")))
                .andExpect(jsonPath("$.modoRetoExclusivo").value(false));
    }
}
