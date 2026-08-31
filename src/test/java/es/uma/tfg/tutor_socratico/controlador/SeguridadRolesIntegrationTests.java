package es.uma.tfg.tutor_socratico.controlador;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SeguridadRolesIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean(answers = Answers.CALLS_REAL_METHODS)
    private ChatLanguageModel chatLanguageModel;

    @BeforeEach
    void configurarMockLlm() {
        when(chatLanguageModel.generate(anyList()))
                .thenReturn(new Response<>(AiMessage.from("respuesta de prueba")));
    }

    @Test
    void alumnoNoPuedeAccederAlPanelDelProfesor() throws Exception {
        mockMvc.perform(get("/api/profesor/resumen").with(user("12345").roles("ALUMNO")))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    void profesorPuedeUsarElTutor() throws Exception {
        mockMvc.perform(post("/api/tutor/chat")
                        .with(user("99991").roles("PROFESOR"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"historial\":[{\"role\":\"user\",\"content\":\"hola\"}],\"tema\":\"General\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void profesorAccedeAlResumen() throws Exception {
        mockMvc.perform(get("/api/profesor/resumen").with(user("99991").roles("PROFESOR")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    void profesorDescargaInformeCsv() throws Exception {
        MvcResult resultado = mockMvc.perform(get("/api/profesor/informe").with(user("99991").roles("PROFESOR")))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(resultado))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(new MediaType("text", "csv")))
                .andExpect(header().string("Content-Disposition", containsString("attachment")));
    }

    @Test
    void loginDeProfesorDevuelveSuRol() throws Exception {
        mockMvc.perform(formLogin().loginProcessingUrl("/login").user("99991").password("91111"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"rol\":\"PROFESOR\"")));
    }
}
