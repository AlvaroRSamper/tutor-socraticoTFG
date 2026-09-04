package es.uma.tfg.tutor_socratico.servicio;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import es.uma.tfg.tutor_socratico.dto.Mensaje;
import es.uma.tfg.tutor_socratico.dto.PeticionChat;
import es.uma.tfg.tutor_socratico.dto.PeticionEjercicio;
import es.uma.tfg.tutor_socratico.dto.RespuestaChat;
import es.uma.tfg.tutor_socratico.perfil.PerfilAprendizajeServicio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ServicioTutorTests {

    private ChatLanguageModel chatLanguageModel;
    private EmbeddingModel embeddingModel;
    private EmbeddingStore<TextSegment> embeddingStore;
    private PerfilAprendizajeServicio perfilAprendizajeServicio;
    private ServicioRegistroConsultas servicioRegistroConsultas;
    private ServicioTutor servicioTutor;

    @BeforeEach
    void configurar() {
        chatLanguageModel = mock(ChatLanguageModel.class, org.mockito.Mockito.CALLS_REAL_METHODS);
        embeddingModel = mock(EmbeddingModel.class);
        @SuppressWarnings("unchecked")
        EmbeddingStore<TextSegment> storeMock = mock(EmbeddingStore.class);
        embeddingStore = storeMock;
        // Repositorio de perfiles con ESTADO: save almacena y findBy devuelve lo almacenado, para que
        // la iteración persistida se acumule entre consultas (antes lo hacía una caché en memoria).
        final java.util.Map<String, es.uma.tfg.tutor_socratico.persistencia.PerfilAlumnoRegistro> perfilAlmacen =
                new java.util.HashMap<>();
        es.uma.tfg.tutor_socratico.persistencia.PerfilAlumnoRepositorio perfilRepositorio =
                mock(es.uma.tfg.tutor_socratico.persistencia.PerfilAlumnoRepositorio.class);
        when(perfilRepositorio.findByUsernameAndAsignaturaId(any(), any())).thenAnswer(inv ->
                java.util.Optional.ofNullable(perfilAlmacen.get(inv.getArgument(0) + "|" + inv.getArgument(1))));
        when(perfilRepositorio.save(any())).thenAnswer(inv -> {
            es.uma.tfg.tutor_socratico.persistencia.PerfilAlumnoRegistro r = inv.getArgument(0);
            perfilAlmacen.put(r.getUsername() + "|" + r.getAsignaturaId(), r);
            return r;
        });
        perfilAprendizajeServicio = new PerfilAprendizajeServicio(perfilRepositorio);

        es.uma.tfg.tutor_socratico.persistencia.AsignaturaRepositorio asignaturaRepositorio =
                mock(es.uma.tfg.tutor_socratico.persistencia.AsignaturaRepositorio.class);
        when(asignaturaRepositorio.findById(anyString())).thenReturn(java.util.Optional.empty());

        when(embeddingModel.embed(anyString()))
                .thenReturn(new Response<>(Embedding.from(new float[]{0.1f, 0.2f})));
        when(embeddingStore.search(any())).thenReturn(new EmbeddingSearchResult<>(List.of()));
        when(chatLanguageModel.generate(org.mockito.ArgumentMatchers.<ChatMessage>anyList()))
                .thenReturn(new Response<>(AiMessage.from("respuesta de prueba")));

        servicioRegistroConsultas = mock(ServicioRegistroConsultas.class);

        servicioTutor = new ServicioTutor(chatLanguageModel, embeddingModel, embeddingStore,
                perfilAprendizajeServicio, servicioRegistroConsultas, asignaturaRepositorio);
        servicioTutor.setFuenteAleatoria(() -> 1.0);
    }

    private PeticionChat peticionConPregunta(String pregunta) {
        return new PeticionChat(List.of(new Mensaje("user", pregunta)), "General");
    }

    @Test
    void primeraIteracionEsFaseDeCalibracionYMuestraOpciones() {
        RespuestaChat respuesta = servicioTutor.consultarTutor(peticionConPregunta("¿Qué es la herencia?"), "12345", null);

        assertThat(respuesta.fase()).isEqualTo("CALIBRACION");
        assertThat(respuesta.mostrarOpciones()).isTrue();
    }

    @Test
    void quintaIteracionEsRegimenPermanenteSinOpciones() {
        for (int i = 1; i <= 4; i++) {
            servicioTutor.consultarTutor(peticionConPregunta("Pregunta " + i), "12345", null);
        }

        RespuestaChat quintaRespuesta = servicioTutor.consultarTutor(peticionConPregunta("Pregunta 5"), "12345", null);

        assertThat(quintaRespuesta.fase()).isEqualTo("PERMANENTE");
        assertThat(quintaRespuesta.mostrarOpciones()).isFalse();
    }

    @Test
    void elSystemPromptLlevaLosValoresExactosDeIteracionYPerfil() {
        servicioTutor.consultarTutor(peticionConPregunta("¿Qué es el polimorfismo?"), "12345", null);

        ArgumentCaptor<List<ChatMessage>> captor = ArgumentCaptor.forClass(List.class);
        org.mockito.Mockito.verify(chatLanguageModel).generate(captor.capture());

        String textoSistema = ((SystemMessage) captor.getValue().get(0)).text();
        assertThat(textoSistema).contains("\"iteracion\": 1");
        assertThat(textoSistema).contains("\"idUsuario\": \"12345\"");
        assertThat(textoSistema).contains("teorico\": 50");
    }

    @Test
    void generarEjercicioNoIncrementaLaIteracionDeChat() {
        servicioTutor.generarEjercicio(new PeticionEjercicio("General", "Media"), "12345", null);
        servicioTutor.generarEjercicio(new PeticionEjercicio("General", "Media"), "12345", null);

        RespuestaChat primeraRespuestaChat = servicioTutor.consultarTutor(peticionConPregunta("Hola"), "12345", null);

        assertThat(primeraRespuestaChat.fase()).isEqualTo("CALIBRACION");
    }

    @Test
    void consultarTutorRegistraLaConsultaEnBaseDeDatos() {
        servicioTutor.consultarTutor(peticionConPregunta("¿Qué es un objeto?"), "12345", null);

        org.mockito.Mockito.verify(servicioRegistroConsultas).registrarChat(
                org.mockito.ArgumentMatchers.eq("12345"),
                isNull(),
                org.mockito.ArgumentMatchers.eq("General"),
                org.mockito.ArgumentMatchers.eq("¿Qué es un objeto?"),
                anyString(),
                org.mockito.ArgumentMatchers.eq("CALIBRACION"),
                org.mockito.ArgumentMatchers.eq(1));
    }

    @Test
    void generarEjercicioRegistraLaConsultaEnBaseDeDatos() {
        servicioTutor.generarEjercicio(new PeticionEjercicio("General", "Media"), "54321", null);

        org.mockito.Mockito.verify(servicioRegistroConsultas).registrarEjercicio(
                org.mockito.ArgumentMatchers.eq("54321"),
                isNull(),
                org.mockito.ArgumentMatchers.eq("General"),
                org.mockito.ArgumentMatchers.eq("Media"),
                anyString());
    }
}
