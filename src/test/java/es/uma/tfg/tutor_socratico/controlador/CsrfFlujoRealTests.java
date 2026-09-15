package es.uma.tfg.tutor_socratico.controlador;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Reproduce el flujo real del navegador con cookies CSRF (sin el postprocesador
 * csrf() que enmascara el problema): primera carga → login → chat, reutilizando
 * en cada paso la cookie XSRF-TOKEN que devuelve el servidor.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class CsrfFlujoRealTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean(answers = Answers.CALLS_REAL_METHODS)
    private ChatLanguageModel chatLanguageModel;

    @BeforeEach
    void mock() {
        when(chatLanguageModel.generate(anyList()))
                .thenReturn(new Response<>(AiMessage.from("respuesta")));
    }

    @Test
    void flujoNavegadorCompleto_primeraCarga_login_chat() throws Exception {
        // 1. Primera carga: el servidor debe entregar una cookie XSRF-TOKEN.
        MvcResult inicio = mockMvc.perform(get("/index.html")).andReturn();
        Cookie xsrf = inicio.getResponse().getCookie("XSRF-TOKEN");
        assertThat(xsrf).as("La primera carga debe entregar cookie XSRF-TOKEN").isNotNull();

        // 2. Login enviando la cookie y su valor en la cabecera (como hace app.js).
        MvcResult login = mockMvc.perform(post("/login")
                        .cookie(xsrf)
                        .header("X-XSRF-TOKEN", xsrf.getValue())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "12345")
                        .param("password", "11111"))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession sesion = (MockHttpSession) login.getRequest().getSession(false);
        // Tras autenticar, Spring ROTA el token: el login entrega dos Set-Cookie (borra el
        // viejo y escribe el nuevo). Un navegador aplica ambos en orden y se queda con el
        // último no vacío, que es lo que modelamos aquí.
        Cookie xsrfTrasLogin = cookieEfectiva(login);
        assertThat(xsrfTrasLogin).as("El login debe entregar la cookie XSRF-TOKEN rotada").isNotNull();
        assertThat(xsrfTrasLogin.getValue()).as("La cookie rotada no puede estar vacía").isNotBlank();

        // 3. Chat con la sesión y el token nuevo: debe funcionar (200), no 403.
        mockMvc.perform(post("/api/tutor/chat")
                        .session(sesion)
                        .cookie(xsrfTrasLogin)
                        .header("X-XSRF-TOKEN", xsrfTrasLogin.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"historial\":[{\"role\":\"user\",\"content\":\"hola\"}],\"tema\":\"General\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void cerrarSesionYVolverAEntrar_sinRecargarLaPagina_noDa403() throws Exception {
        // 1. Primera carga + login (igual que el flujo del navegador).
        MvcResult inicio = mockMvc.perform(get("/index.html")).andReturn();
        Cookie xsrf = inicio.getResponse().getCookie("XSRF-TOKEN");

        MvcResult login = mockMvc.perform(post("/login")
                        .cookie(xsrf)
                        .header("X-XSRF-TOKEN", xsrf.getValue())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "12345")
                        .param("password", "11111"))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession sesion = (MockHttpSession) login.getRequest().getSession(false);
        Cookie xsrfTrasLogin = cookieEfectiva(login);

        // 2. Logout: CsrfLogoutHandler borra la cookie. El manejador de logout debe
        //    materializar una nueva, porque la pantalla de login es un overlay sin
        //    recarga y el siguiente POST /login reutiliza lo que haya en el navegador.
        MvcResult logout = mockMvc.perform(post("/logout")
                        .session(sesion)
                        .cookie(xsrfTrasLogin)
                        .header("X-XSRF-TOKEN", xsrfTrasLogin.getValue()))
                .andExpect(status().isOk())
                .andReturn();

        Cookie xsrfTrasLogout = cookieEfectiva(logout);
        assertThat(xsrfTrasLogout)
                .as("El logout debe dejar una cookie XSRF-TOKEN nueva; si solo borra la vieja, "
                        + "el siguiente login falla con el 403 de 'rol'")
                .isNotNull();

        // 3. Segundo login sin recargar la página: debe ser 200, no 403.
        mockMvc.perform(post("/login")
                        .cookie(xsrfTrasLogout)
                        .header("X-XSRF-TOKEN", xsrfTrasLogout.getValue())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "12345")
                        .param("password", "11111"))
                .andExpect(status().isOk());
    }

    /** Última cookie XSRF-TOKEN con valor no vacío, como quedaría en el navegador. */
    private Cookie cookieEfectiva(MvcResult resultado) {
        Cookie efectiva = null;
        for (Cookie c : resultado.getResponse().getCookies()) {
            if ("XSRF-TOKEN".equals(c.getName()) && c.getValue() != null && !c.getValue().isBlank()) {
                efectiva = c;
            }
        }
        return efectiva;
    }
}
