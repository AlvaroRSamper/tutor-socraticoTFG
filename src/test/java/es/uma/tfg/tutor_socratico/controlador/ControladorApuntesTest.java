package es.uma.tfg.tutor_socratico.controlador;

import es.uma.tfg.tutor_socratico.dto.ApunteResumen;
import es.uma.tfg.tutor_socratico.dto.RespuestaApunteGuardado;
import es.uma.tfg.tutor_socratico.servicio.ServicioValidacionApuntes;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pruebas de integración web para {@link ControladorApuntes} (/api/alumno/apuntes).
 * Se ejercita la subida de apuntes mediante {@link MockMultipartFile}, las validaciones de
 * entrada (400), la seguridad frente a peticiones sin autenticar (401) y las operaciones de
 * listado y borrado. El {@link ServicioValidacionApuntes} se sustituye por un mock para forzar
 * escenarios concretos sin depender del LLM ni del almacén vectorial.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ControladorApuntesTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ServicioValidacionApuntes servicioValidacion;

    // ---------------- /validar ----------------

    @Test
    void validarConArchivoTxtDevuelveFeedback() throws Exception {
        when(servicioValidacion.validar(anyString(), anyString(), anyString()))
                .thenReturn("### ✅ Lo que está bien\nTodo correcto");

        MockMultipartFile archivo = new MockMultipartFile(
                "archivo", "apuntes.txt", "text/plain",
                "La herencia permite reutilizar código".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/alumno/apuntes/validar")
                        .file(archivo)
                        .param("tema", "Herencia")
                        .with(user("12345").roles("ALUMNO"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.feedback").exists())
                .andExpect(jsonPath("$.contenido").exists());
    }

    @Test
    void validarSinContenidoDevuelve400() throws Exception {
        mockMvc.perform(post("/api/alumno/apuntes/validar")
                        .with(user("12345").roles("ALUMNO"))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").exists());
    }

    @Test
    void validarSinAutenticarDevuelve401() throws Exception {
        mockMvc.perform(post("/api/alumno/apuntes/validar")
                        .param("texto", "algo")
                        .with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    // ---------------- /guardar ----------------

    @Test
    void guardarConTituloYTextoDevuelveExito() throws Exception {
        when(servicioValidacion.guardar(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new RespuestaApunteGuardado(true, "Mis apuntes", "Apuntes guardados."));

        mockMvc.perform(post("/api/alumno/apuntes/guardar")
                        .param("titulo", "Mis apuntes")
                        .param("texto", "Contenido de los apuntes")
                        .with(user("12345").roles("ALUMNO"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exito").value(true));
    }

    @Test
    void guardarConTituloEnBlancoDevuelve400() throws Exception {
        mockMvc.perform(post("/api/alumno/apuntes/guardar")
                        .param("titulo", "   ")
                        .param("texto", "Contenido de los apuntes")
                        .with(user("12345").roles("ALUMNO"))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").exists());
    }

    @Test
    void guardarSinContenidoDevuelve400() throws Exception {
        mockMvc.perform(post("/api/alumno/apuntes/guardar")
                        .param("titulo", "Mis apuntes")
                        .with(user("12345").roles("ALUMNO"))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").exists());
    }

    // ---------------- /listar ----------------

    @Test
    void listarDevuelveLosApuntesDelAlumno() throws Exception {
        when(servicioValidacion.listar(anyString(), anyString()))
                .thenReturn(List.of(new ApunteResumen(1L, "Herencia", "2026-01-01T10:00")));

        mockMvc.perform(get("/api/alumno/apuntes/listar")
                        .with(user("12345").roles("ALUMNO")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].tema").value("Herencia"));
    }

    @Test
    void listarSinAutenticarDevuelve401() throws Exception {
        mockMvc.perform(get("/api/alumno/apuntes/listar"))
                .andExpect(status().isUnauthorized());
    }

    // ---------------- DELETE /{id} ----------------

    @Test
    void borrarApunteExistenteDevuelve200() throws Exception {
        when(servicioValidacion.borrar(eq(7L), anyString())).thenReturn(true);

        mockMvc.perform(delete("/api/alumno/apuntes/7")
                        .with(user("12345").roles("ALUMNO"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exito").value(true));
    }

    @Test
    void borrarApunteInexistenteDevuelve400() throws Exception {
        when(servicioValidacion.borrar(anyLong(), anyString())).thenReturn(false);

        mockMvc.perform(delete("/api/alumno/apuntes/999")
                        .with(user("12345").roles("ALUMNO"))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.exito").value(false));
    }

    @Test
    void borrarSinAutenticarDevuelve401() throws Exception {
        mockMvc.perform(delete("/api/alumno/apuntes/7").with(csrf()))
                .andExpect(status().isUnauthorized());
    }
}
