package es.uma.tfg.tutor_socratico.controlador;

import es.uma.tfg.tutor_socratico.dto.RespuestaChat;
import es.uma.tfg.tutor_socratico.servicio.ServicioTutor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifica el rate limiting (Bucket4j) de los endpoints que invocan al LLM. Se fija una capacidad
 * baja por propiedad de test: superarla debe devolver 429.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {"tutor.rate-limit.capacidad=3", "tutor.rate-limit.minutos=1"})
class RateLimitIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ServicioTutor servicioTutor;

    private static final String CUERPO =
            "{\"historial\":[{\"role\":\"user\",\"content\":\"hola\"}],\"tema\":\"General\"}";

    @Test
    void superarLaCuotaDevuelve429() throws Exception {
        when(servicioTutor.consultarTutor(any(), anyString(), anyString()))
                .thenReturn(RespuestaChat.error("ok"));

        // Las primeras 3 peticiones consumen la cuota.
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/tutor/chat")
                            .with(user("rate-user").roles("ALUMNO"))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(CUERPO))
                    .andExpect(status().isOk());
        }

        // La cuarta se rechaza con 429.
        mockMvc.perform(post("/api/tutor/chat")
                        .with(user("rate-user").roles("ALUMNO"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.mensaje").exists());
    }
}
