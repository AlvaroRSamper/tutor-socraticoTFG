package es.uma.tfg.tutor_socratico.configuracion;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import java.time.Duration;


@Slf4j
@Configuration
public class Config_Rag {

    @Value("${anthropic.api.key}")
    private String anthropicApiKey;

    @Value("${anthropic.model}")
    private String anthropicModel;

    @Value("${anthropic.max-tokens}")
    private int anthropicMaxTokens;

    @Value("${anthropic.max-tokens-extenso}")
    private int anthropicMaxTokensExtenso;

    @Value("${anthropic.timeout-seconds}")
    private long anthropicTimeoutSeconds;

    @Bean
    @Primary
    public ChatLanguageModel chatLanguageModel() {
        // Si no hay API key (p. ej. desarrollo local), se usa un valor placeholder para que la
        // aplicación ARRANQUE igualmente. Las llamadas reales al modelo fallarán de forma controlada
        // (los servicios capturan el error y devuelven un aviso), pero el resto de la app funciona.
        return construirModelo(anthropicMaxTokens);
    }

    @Bean
    public ChatLanguageModel chatLanguageModelExtenso() {
        return construirModelo(anthropicMaxTokensExtenso);
    }

    private ChatLanguageModel construirModelo(int maxTokens) {
        String apiKey = (anthropicApiKey == null || anthropicApiKey.isBlank()) ? "sk-ant-sin-configurar" : anthropicApiKey;
        if (anthropicApiKey == null || anthropicApiKey.isBlank()) {
            log.warn("ANTHROPIC_API_KEY no configurada: el tutor arrancará pero las respuestas del LLM no estarán disponibles :/");
        }
        return AnthropicChatModel.builder()
                .apiKey(apiKey)
                .modelName(anthropicModel)
                .maxTokens(maxTokens)
                .timeout(Duration.ofSeconds(anthropicTimeoutSeconds))
                .build();
    }

    @Bean
    public EmbeddingModel embeddingModel() {
        return new AllMiniLmL6V2QuantizedEmbeddingModel();
    }


    @Bean
    @Profile("prod")
    public EmbeddingStore<TextSegment> pgVectorEmbeddingStore(
            @Value("${tutor.db.host}") String host,
            @Value("${tutor.db.port}") int port,
            @Value("${tutor.db.name}") String database,
            @Value("${tutor.db.user}") String user,
            @Value("${tutor.db.password}") String password,
            @Value("${tutor.pgvector.table}") String table,
            @Value("${tutor.pgvector.dimension}") int dimension) {
        log.info("Inicializando almacén de vectores pgvector (tabla '{}', dimensión {})", table, dimension);
        return PgVectorEmbeddingStore.builder()
                .host(host)
                .port(port)
                .database(database)
                .user(user)
                .password(password)
                .table(table)
                .dimension(dimension)
                .createTable(true)
                .build();
    }


    @Bean
    @Profile("!prod")
    public EmbeddingStore<TextSegment> inMemoryEmbeddingStore() {
        log.info("Inicializando almacén de vectores en memoria (perfil no-producción).");
        return new InMemoryEmbeddingStore<>();
    }
}
