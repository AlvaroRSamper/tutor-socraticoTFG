package es.uma.tfg.tutor_socratico.configuracion;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
public class ConfiguracionRag {

    @Value("${anthropic.api.key}")
    private String anthropicApiKey;

    @Bean
    public ChatLanguageModel chatLanguageModel() {
        return AnthropicChatModel.builder()
                .apiKey(anthropicApiKey)
                .modelName("claude-opus-4-7")
                .maxTokens(1024)
                .build();
    }

    @Bean
    public EmbeddingModel embeddingModel() {
        return new AllMiniLmL6V2QuantizedEmbeddingModel();
    }

    @Bean
    public EmbeddingStore<TextSegment> embeddingStore(EmbeddingModel embeddingModel) {
        InMemoryEmbeddingStore<TextSegment> almacenVectores = new InMemoryEmbeddingStore<>();

        EmbeddingStoreIngestor inyector = EmbeddingStoreIngestor.builder()
                .documentSplitter(DocumentSplitters.recursive(500, 50))
                .embeddingModel(embeddingModel)
                .embeddingStore(almacenVectores)
                .build();

        try {
            PathMatchingResourcePatternResolver buscadorRecursos = new PathMatchingResourcePatternResolver();
            Resource[] recursos = buscadorRecursos.getResources("classpath:apuntes/*.pdf");
            
            List<Document> documentos = new ArrayList<>();
            ApachePdfBoxDocumentParser lectorPdf = new ApachePdfBoxDocumentParser();
            
            for (Resource recurso : recursos) {
                try (InputStream flujoEntrada = recurso.getInputStream()) {
                    Document documento = lectorPdf.parse(flujoEntrada);
                    documento.metadata().add("tema", recurso.getFilename());
                    documentos.add(documento);
                    log.info("Cargado PDF: {}", recurso.getFilename());
                } catch (Exception e) {
                    log.error("Error procesando PDF: {}", recurso.getFilename(), e);
                }
            }
            
            if (!documentos.isEmpty()) {
                log.info("Vectorizando {} documentos...", documentos.size());
                inyector.ingest(documentos);
                log.info("Vectorizacion completada.");
            } else {
                log.warn("Directorio de apuntes vacio.");
            }
            
        } catch (IOException e) {
            log.error("Error al acceder a los recursos PDF", e);
        }

        return almacenVectores;
    }
}
