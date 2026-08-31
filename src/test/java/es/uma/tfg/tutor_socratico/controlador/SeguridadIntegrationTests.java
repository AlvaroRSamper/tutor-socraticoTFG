package es.uma.tfg.tutor_socratico.controlador;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.mockito.Answers;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SeguridadIntegrationTests {

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
    void recursosEstaticosAccesiblesSinLogin() throws Exception {
        mockMvc.perform(get("/index.html"))
                .andExpect(status().isOk());
    }

    @Test
    void chatSinAutenticarDevuelve401() throws Exception {
        mockMvc.perform(post("/api/tutor/chat")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"historial\":[{\"role\":\"user\",\"content\":\"hola\"}],\"tema\":\"General\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginConCredencialesValidasDevuelve200() throws Exception {
        mockMvc.perform(formLogin().loginProcessingUrl("/login").user("12345").password("11111"))
                .andExpect(status().isOk());
    }

    @Test
    void loginConCredencialesInvalidasDevuelve401() throws Exception {
        mockMvc.perform(formLogin().loginProcessingUrl("/login").user("12345").password("incorrecta"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void chatAutenticadoDevuelve200() throws Exception {
        mockMvc.perform(post("/api/tutor/chat")
                        .with(user("12345").roles("ALUMNO"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"historial\":[{\"role\":\"user\",\"content\":\"hola\"}],\"tema\":\"General\"}"))
                .andExpect(status().isOk());
    }
}
