package es.uma.tfg.tutor_socratico.controlador;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pruebas unitarias para {@link ManejadorExcepciones}, el interceptor global de excepciones.
 * Se monta un MockMvc en modo "standalone" con un controlador ficticio y el advice bajo prueba,
 * sin arrancar el contexto de Spring. Así se comprueba de forma aislada que una excepción no
 * controlada produce un 500 con JSON y que un fallo de validación de un DTO produce un 400.
 */
class ManejadorExcepcionesTest {

    private MockMvc mockMvc;

    @BeforeEach
    void configurar() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ControladorFicticio())
                .setControllerAdvice(new ManejadorExcepciones())
                .build();
    }

    @Test
    void excepcionGeneralNoControladaDevuelve500ConJson() throws Exception {
        mockMvc.perform(get("/prueba/error"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.mensaje").exists());
    }

    @Test
    void falloDeValidacionDeArgumentosDevuelve400ConJson() throws Exception {
        mockMvc.perform(post("/prueba/validar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.mensaje").exists());
    }

    // ---------------- Infraestructura de prueba ----------------

    @RestController
    static class ControladorFicticio {

        @GetMapping("/prueba/error")
        public String lanzarError() {
            throw new RuntimeException("fallo inesperado de prueba");
        }

        @PostMapping("/prueba/validar")
        public String validar(@Valid @RequestBody CuerpoFicticio cuerpo) {
            return cuerpo.campo();
        }
    }

    record CuerpoFicticio(@NotBlank String campo) {}
}
