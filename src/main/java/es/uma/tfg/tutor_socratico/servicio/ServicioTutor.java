package es.uma.tfg.tutor_socratico.servicio;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

import es.uma.tfg.tutor_socratico.dto.PeticionChat;
import es.uma.tfg.tutor_socratico.dto.PeticionEjercicio;
import es.uma.tfg.tutor_socratico.dto.Mensaje;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class ServicioTutor {

    private final ChatLanguageModel chatLanguageModel;
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;

    public ServicioTutor(ChatLanguageModel chatLanguageModel, 
                        EmbeddingModel embeddingModel, 
                        EmbeddingStore<TextSegment> embeddingStore) {
        this.chatLanguageModel = chatLanguageModel;
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
    }

    public Map<String, String> consultarTutor(PeticionChat peticion) {
        String ultimaPregunta = peticion.historial().get(peticion.historial().size() - 1).content();
        
        dev.langchain4j.data.embedding.Embedding vectorPregunta = embeddingModel.embed(ultimaPregunta).content();
            
        var searchBuilder = dev.langchain4j.store.embedding.EmbeddingSearchRequest.builder()
                .queryEmbedding(vectorPregunta)
                .maxResults(4)
                .minScore(0.5);

        if (peticion.tema() != null && !peticion.tema().trim().isEmpty() && !peticion.tema().equalsIgnoreCase("General")) {
            searchBuilder.filter(metadataKey("tema").isEqualTo(peticion.tema()));
        }

        List<EmbeddingMatch<TextSegment>> resultados = embeddingStore.search(searchBuilder.build()).matches();

            StringBuilder contexto = new StringBuilder();
            for(EmbeddingMatch<TextSegment> resultado : resultados) {
                String fuente = resultado.embedded().metadata().getString("tema");
                contexto.append(resultado.embedded().text())
                       .append("\n(Fuente: ").append(fuente).append(")\n\n");
            }

        String textoSistema = "Eres un tutor socrático experto en Programación Orientada a Objetos en Java. " +
                "Tu objetivo es guiar al estudiante haciendo preguntas y evitar darle la solución de código de forma directa. " +
                "Utiliza el siguiente contexto extraído de sus apuntes oficiales para guiarle. " +
                "Si es oportuno, menciónale sutilmente el nombre del archivo fuente del que debe repasar la teoría.\n\n" +
                "Contexto de los apuntes:\n" + contexto.toString();

            List<ChatMessage> mensajesChat = new ArrayList<>();
            mensajesChat.add(SystemMessage.from(textoSistema));

            for(Mensaje m : peticion.historial()) {
                if(m.role().equals("user")) mensajesChat.add(UserMessage.from(m.content()));
                if(m.role().equals("assistant")) mensajesChat.add(AiMessage.from(m.content()));
            }

            Response<AiMessage> respuesta = chatLanguageModel.generate(mensajesChat);
        return Map.of("mensaje", respuesta.content().text());
    }

    public Map<String, String> generarEjercicio(PeticionEjercicio peticion) {
        String preguntaBase = "ejercicios conceptos teoria ejemplos";
        dev.langchain4j.data.embedding.Embedding vectorPregunta = embeddingModel.embed(preguntaBase).content();
            
        var searchBuilder = dev.langchain4j.store.embedding.EmbeddingSearchRequest.builder()
                .queryEmbedding(vectorPregunta)
                .maxResults(5);

        if (peticion.tema() != null && !peticion.tema().trim().isEmpty() && !peticion.tema().equalsIgnoreCase("General")) {
            searchBuilder.filter(metadataKey("tema").isEqualTo(peticion.tema()));
        }

        List<EmbeddingMatch<TextSegment>> resultados = embeddingStore.search(searchBuilder.build()).matches();

            StringBuilder contexto = new StringBuilder();
            for(EmbeddingMatch<TextSegment> resultado : resultados) {
                contexto.append(resultado.embedded().text()).append("\n");
            }

        String instruccion = "Genera un ejercicio práctico de Programación Orientada a Objetos en Java. " +
                "Tema principal: " + peticion.tema() + ". " +
                "Nivel de Dificultad: " + peticion.dificultad() + ". " +
                "Usa los siguientes extractos de los apuntes del alumno como inspiración para el tipo de conceptos que debe saber aplicar:\n\n" + contexto.toString() + "\n\n" +
                "Por favor, redacta solo el enunciado del problema de forma clara, como si fuera un examen. " +
                "Evita incluir código de solución o pistas directas. Formatea el texto de forma amigable usando listas o negritas.";

        Response<AiMessage> respuesta = chatLanguageModel.generate(
                SystemMessage.from("Eres un profesor universitario diseñando exámenes."),
                UserMessage.from(instruccion)
        );

            return Map.of("mensaje", respuesta.content().text());
    }
}
