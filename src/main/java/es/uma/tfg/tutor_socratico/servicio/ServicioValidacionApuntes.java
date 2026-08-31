package es.uma.tfg.tutor_socratico.servicio;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import es.uma.tfg.tutor_socratico.dto.ApunteResumen;
import es.uma.tfg.tutor_socratico.dto.RespuestaApunteGuardado;
import es.uma.tfg.tutor_socratico.persistencia.ApunteAlumno;
import es.uma.tfg.tutor_socratico.persistencia.ApunteAlumnoRepositorio;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


@Slf4j
@Service
public class ServicioValidacionApuntes {

    private static final int MAX_CHARS_APUNTE = 20_000;

    private static final String PROMPT_SISTEMA =
            "Eres un profesor universitario experto que revisa los apuntes de un alumno comparándolos " +
            "con la base de conocimiento oficial de la asignatura. Sé riguroso, pedagógico y constructivo.";

    private final ChatLanguageModel chatLanguageModel;
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final ApunteAlumnoRepositorio apunteRepositorio;
    private final ServicioIngesta servicioIngesta;

    public ServicioValidacionApuntes(ChatLanguageModel chatLanguageModel,
                                     EmbeddingModel embeddingModel,
                                     EmbeddingStore<TextSegment> embeddingStore,
                                     ApunteAlumnoRepositorio apunteRepositorio,
                                     ServicioIngesta servicioIngesta) {
        this.chatLanguageModel = chatLanguageModel;
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.apunteRepositorio = apunteRepositorio;
        this.servicioIngesta = servicioIngesta;
    }

    
    public String validar(String contenidoAlumno, String tema, String asignaturaId) {
        if (contenidoAlumno == null || contenidoAlumno.isBlank()) {
            return "⚠️ No se ha recibido texto de apuntes para validar.";
        }
        String recorte = contenidoAlumno.length() > MAX_CHARS_APUNTE
                ? contenidoAlumno.substring(0, MAX_CHARS_APUNTE) + "\n... (apuntes truncados por longitud) ..."
                : contenidoAlumno;

        String contextoProfesor = contextoSoloProfesor(recorte, tema, asignaturaId);

        String instruccion =
                "Aquí tienes los apuntes de un alumno. Compáralos con la base de conocimiento del profesor " +
                "suministrada. Analiza qué está bien, qué conceptos son erróneos y qué le falta por añadir.\n\n" +
                "Devuelve el análisis en Markdown con estas tres secciones exactas:\n" +
                "### ✅ Lo que está bien\n### ❌ Conceptos erróneos o imprecisos\n### ➕ Lo que falta por añadir\n\n" +
                "Si la base del profesor está vacía o no cubre el tema, indícalo con honestidad y valora los apuntes " +
                "según buenas prácticas generales de la materia.\n\n" +
                "=== BASE DE CONOCIMIENTO DEL PROFESOR ===\n" +
                (contextoProfesor.isBlank() ? "(sin material oficial relevante encontrado)" : contextoProfesor) + "\n\n" +
                "=== APUNTES DEL ALUMNO ===\n" + recorte;

        try {
            Response<AiMessage> r = chatLanguageModel.generate(
                    SystemMessage.from(PROMPT_SISTEMA), UserMessage.from(instruccion));
            return (r != null && r.content() != null) ? r.content().text()
                    : "⚠️ No se ha podido generar la validación de los apuntes.";
        } catch (Throwable e) {
            log.error("Error validando apuntes del alumno: {}", e.getMessage(), e);
            return "⚠️ **Aviso de IA:** No se ha podido validar los apuntes en este momento. Verifica tu conexión o la clave de API.";
        }
    }

    
    public RespuestaApunteGuardado guardar(String username, String asignaturaId, String titulo, String contenido) {
        String asig = (asignaturaId == null || asignaturaId.isBlank()) ? "General" : asignaturaId;
        String tema = (titulo == null || titulo.isBlank()) ? "Apuntes propios" : titulo.trim();

        ApunteAlumno apunte = ApunteAlumno.builder()
                .username(username)
                .asignaturaId(asig)
                .tema(tema)
                .contenidoOriginal(contenido)
                .fechaSubida(LocalDateTime.now())
                .build();
        apunteRepositorio.save(apunte);

        servicioIngesta.ingerirTextoApunteAlumno(asig, username, tema, contenido);

        return new RespuestaApunteGuardado(true, tema, "Apuntes guardados e indexados en 'Mis Conocimientos'.");
    }

    
    public List<ApunteResumen> listar(String username, String asignaturaId) {
        String asig = (asignaturaId == null || asignaturaId.isBlank()) ? "General" : asignaturaId;
        return apunteRepositorio.findByUsernameAndAsignaturaIdOrderByFechaSubidaDesc(username, asig)
                .stream()
                .map(a -> new ApunteResumen(
                        a.getId(),
                        a.getTema(),
                        a.getFechaSubida() != null ? a.getFechaSubida().toString() : ""))
                .toList();
    }

    public boolean borrar(Long id, String username) {
        return apunteRepositorio.findById(id).map(a -> {
            if (a.getUsername().equals(username)) {
                apunteRepositorio.delete(a);
                servicioIngesta.borrarApunteAlumno(a.getAsignaturaId(), username, a.getTema());
                return true;
            }
            return false;
        }).orElse(false);
    }

    

    private String contextoSoloProfesor(String consulta, String tema, String asignaturaId) {
        StringBuilder contexto = new StringBuilder();
        try {
            Embedding vector = embeddingModel.embed(consulta).content();
            var builder = EmbeddingSearchRequest.builder().queryEmbedding(vector).maxResults(6);

            Filter filtro = metadataKey("username").isEqualTo(ServicioIngesta.AUTOR_PROFESOR);
            if (asignaturaId != null && !asignaturaId.isBlank()) {
                filtro = filtro.and(metadataKey("asignatura_id").isEqualTo(asignaturaId));
            }
            if (tema != null && !tema.isBlank() && !tema.equalsIgnoreCase("General")) {
                filtro = filtro.and(metadataKey("tema").isEqualTo(tema));
            }
            builder.filter(filtro);

            List<EmbeddingMatch<TextSegment>> matches = embeddingStore.search(builder.build()).matches();
            for (EmbeddingMatch<TextSegment> m : new ArrayList<>(matches)) {
                if (m != null && m.embedded() != null && m.embedded().text() != null) {
                    contexto.append(m.embedded().text()).append("\n\n");
                }
            }
        } catch (Throwable t) {
            log.warn("No se pudo recuperar contexto del profesor para validar apuntes: {}", t.getMessage());
        }
        return contexto.toString();
    }
}
