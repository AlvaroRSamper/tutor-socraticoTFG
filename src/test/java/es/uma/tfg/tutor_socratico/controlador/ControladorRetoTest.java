package es.uma.tfg.tutor_socratico.controlador;

import es.uma.tfg.tutor_socratico.dto.DetalleEjercicioDTO;
import es.uma.tfg.tutor_socratico.dto.RespuestaChatReto;
import es.uma.tfg.tutor_socratico.dto.RespuestaEstadoReto;
import es.uma.tfg.tutor_socratico.dto.RespuestaPublicacion;
import es.uma.tfg.tutor_socratico.excepcion.RecursoNoEncontradoException;
import es.uma.tfg.tutor_socratico.servicio.ServicioReto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ControladorRetoTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ServicioReto servicioReto;

    @Test
    void crearIaConDatosValidosDevuelve200() throws Exception {
        when(servicioReto.crearEjercicioIa(any(), anyString(), anyString()))
                .thenReturn(new DetalleEjercicioDTO(1L, "T", "E", "Java", List.of()));

        mockMvc.perform(post("/api/reto/crear-ia")
                        .with(user("12345").roles("ALUMNO"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tema\":\"Herencia\",\"dificultad\":\"media\",\"lenguaje\":\"Java\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ejercicioId").value(1));
    }

    @Test
    void crearIaSinTemaDevuelve400() throws Exception {
        mockMvc.perform(post("/api/reto/crear-ia")
                        .with(user("12345").roles("ALUMNO"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dificultad\":\"media\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").exists());
    }

    @Test
    void iniciarConEjercicioIdDevuelve200() throws Exception {
        when(servicioReto.iniciarResolucion(anyLong(), anyString(), anyString(), eq(false)))
                .thenReturn(new RespuestaEstadoReto(10L, 5L, "T", "E", "Java", 100, 100, 100, false, "", List.of(), 0, false));

        mockMvc.perform(post("/api/reto/iniciar")
                        .with(user("12345").roles("ALUMNO"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ejercicioId\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resolucionId").value(10))
                .andExpect(jsonPath("$.retomado").value(false));
    }

    @Test
    void iniciarConReiniciarPasaLaOpcionAlServicio() throws Exception {
        when(servicioReto.iniciarResolucion(eq(5L), eq("12345"), anyString(), eq(true)))
                .thenReturn(new RespuestaEstadoReto(11L, 5L, "T", "E", "Java", 100, 100, 100, false, "", List.of(), 0, false));

        mockMvc.perform(post("/api/reto/iniciar")
                        .with(user("12345").roles("ALUMNO"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ejercicioId\":5,\"reiniciar\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resolucionId").value(11));

        verify(servicioReto).iniciarResolucion(eq(5L), eq("12345"), anyString(), eq(true));
    }

    @Test
    void tiempoValidoDevuelve204() throws Exception {
        mockMvc.perform(post("/api/reto/tiempo")
                        .with(user("12345").roles("ALUMNO"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resolucionId\":10,\"segundos\":60}"))
                .andExpect(status().isNoContent());

        verify(servicioReto).sumarTiempo(10L, 60, "12345");
    }

    @Test
    void tiempoNegativoDevuelve400() throws Exception {
        mockMvc.perform(post("/api/reto/tiempo")
                        .with(user("12345").roles("ALUMNO"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resolucionId\":10,\"segundos\":-5}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void iniciarSinEjercicioIdDevuelve400() throws Exception {
        mockMvc.perform(post("/api/reto/iniciar")
                        .with(user("12345").roles("ALUMNO"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void chatConHistorialValidoDevuelve200() throws Exception {
        when(servicioReto.chatReto(any(), anyString(), anyString()))
                .thenReturn(new RespuestaChatReto("Pista socrática"));

        mockMvc.perform(post("/api/reto/chat")
                        .with(user("12345").roles("ALUMNO"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resolucionId\":1,\"historial\":[{\"role\":\"user\",\"content\":\"hola\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value("Pista socrática"));
    }

    @Test
    void chatSinHistorialNiResolucionDevuelve400() throws Exception {
        mockMvc.perform(post("/api/reto/chat")
                        .with(user("12345").roles("ALUMNO"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").exists());
    }

    @Test
    void chatConCuerpoMalFormadoDevuelve400() throws Exception {
        mockMvc.perform(post("/api/reto/chat")
                        .with(user("12345").roles("ALUMNO"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ esto no es json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").exists());
    }

    @Test
    void chatSobreResolucionAjenaOInexistenteDevuelve404() throws Exception {
        when(servicioReto.chatReto(any(), anyString(), anyString()))
                .thenThrow(new RecursoNoEncontradoException("Resolución no encontrada: 999"));

        mockMvc.perform(post("/api/reto/chat")
                        .with(user("12345").roles("ALUMNO"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resolucionId\":999,\"historial\":[{\"role\":\"user\",\"content\":\"hola\"}]}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").exists());
    }

    @Test
    void chatSinAutenticarDevuelve401() throws Exception {
        mockMvc.perform(post("/api/reto/chat")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resolucionId\":1,\"historial\":[{\"role\":\"user\",\"content\":\"hola\"}]}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void profesorPublicaEjercicioDevuelve200() throws Exception {
        when(servicioReto.publicarEjercicioPropuesto(any(), anyString(), anyString()))
                .thenReturn(new RespuestaPublicacion(true, 1L, "Éxito"));

        String cuerpo = "{\"titulo\":\"Reto 1\",\"enunciado\":\"Implementa una pila\","
                + "\"microhitos\":[{\"orden\":1,\"titulo\":\"Clase Pila\",\"descripcion\":\"Crea la clase\"}]}";

        mockMvc.perform(post("/api/reto/profesor/publicar")
                        .with(user("99991").roles("PROFESOR"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exito").value(true));
    }

    @Test
    void alumnoNoPuedePublicarEjercicio() throws Exception {
        String cuerpo = "{\"titulo\":\"Reto 1\",\"enunciado\":\"Implementa una pila\","
                + "\"microhitos\":[{\"orden\":1,\"titulo\":\"Clase Pila\",\"descripcion\":\"Crea la clase\"}]}";

        mockMvc.perform(post("/api/reto/profesor/publicar")
                        .with(user("12345").roles("ALUMNO"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo))
                .andExpect(status().isForbidden());
    }
}
