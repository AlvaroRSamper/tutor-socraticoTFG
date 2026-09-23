package es.uma.tfg.tutor_socratico.servicio;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.filter.Filter;
import es.uma.tfg.tutor_socratico.persistencia.Asignatura;
import es.uma.tfg.tutor_socratico.persistencia.AsignaturaRepositorio;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;


@Slf4j
@Service
public class ServicioIngesta {

    
    public static final String AUTOR_PROFESOR = "PROFESOR";

    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;
    private final AsignaturaRepositorio asignaturaRepositorio;

    public ServicioIngesta(EmbeddingStore<TextSegment> embeddingStore,
                           EmbeddingModel embeddingModel,
                           AsignaturaRepositorio asignaturaRepositorio) {
        this.embeddingStore = embeddingStore;
        this.embeddingModel = embeddingModel;
        this.asignaturaRepositorio = asignaturaRepositorio;
    }

    public int configurarAsignatura(String asignaturaId, String systemPrompt, MultipartFile[] archivos) {
        return configurarAsignatura(asignaturaId, null, systemPrompt, null, null, null, null, archivos);
    }

    public int configurarAsignatura(String asignaturaId, String titulo, String systemPrompt, String colorTema, MultipartFile[] archivos) {
        return configurarAsignatura(asignaturaId, titulo, systemPrompt, colorTema, null, null, null, archivos);
    }

    public int configurarAsignatura(String asignaturaId, String titulo, String systemPrompt, String colorTema,
                                    Integer sensibilidad, String emailProfesor, Integer diaInformeSemanal,
                                    MultipartFile[] archivos) {
        Asignatura asigActual = asignaturaRepositorio.findById(asignaturaId).orElse(null);
        String temasActuales = (asigActual != null && asigActual.getTemas() != null) ? asigActual.getTemas() : "";
        String tituloActual = (titulo != null && !titulo.isBlank()) ? titulo : ((asigActual != null && asigActual.getTitulo() != null) ? asigActual.getTitulo() : "Tutor Socrático");
        String colorActual = (colorTema != null && !colorTema.isBlank()) ? colorTema : ((asigActual != null && asigActual.getColorTema() != null) ? asigActual.getColorTema() : "github-dark");

        Integer sensibilidadActual = (sensibilidad != null) ? sensibilidad
                : ((asigActual != null && asigActual.getSensibilidad() != null) ? asigActual.getSensibilidad() : 5);

        String emailActual = (emailProfesor != null) ? (emailProfesor.isBlank() ? null : emailProfesor.trim())
                : (asigActual != null ? asigActual.getEmailProfesor() : null);
        Integer diaActual = (diaInformeSemanal != null) ? acotarDiaSemana(diaInformeSemanal)
                : (asigActual != null ? asigActual.getDiaInformeSemanal() : null);

        if (archivos != null && archivos.length > 0) {
            List<String> listaTemas = new ArrayList<>();
            if (!temasActuales.isBlank()) {
                for (String t : temasActuales.split(",")) {
                    if (!t.isBlank() && !listaTemas.contains(t.trim())) {
                        listaTemas.add(t.trim());
                    }
                }
            }
            for (MultipartFile archivo : archivos) {
                if (!archivo.isEmpty()) {
                    String nom = archivo.getOriginalFilename();
                    if (nom != null && !nom.isBlank() && !listaTemas.contains(nom.trim())) {
                        listaTemas.add(nom.trim());
                    }
                }
            }
            temasActuales = String.join(",", listaTemas);
        }

        asignaturaRepositorio.save(Asignatura.builder()
                .asignaturaId(asignaturaId)
                .titulo(tituloActual)
                .systemPrompt(systemPrompt)
                .colorTema(colorActual)
                .temas(temasActuales)
                .sensibilidad(sensibilidadActual)
                .emailProfesor(emailActual)
                .diaInformeSemanal(diaActual)
                .modoRetoExclusivo(asigActual != null && Boolean.TRUE.equals(asigActual.getModoRetoExclusivo()))
                .build());

        if (archivos == null || archivos.length == 0) {
            return 0;
        }
        return ingerirApuntes(asignaturaId, archivos);
    }

    private Integer acotarDiaSemana(Integer dia) {
        if (dia == null || dia < 1 || dia > 7) return null;
        return dia;
    }

    public boolean borrarArchivo(String asignaturaId, String nombreArchivo) {
        Asignatura asigActual = asignaturaRepositorio.findById(asignaturaId).orElse(null);
        if (asigActual != null && asigActual.getTemas() != null) {
            List<String> lista = new ArrayList<>();
            for (String t : asigActual.getTemas().split(",")) {
                if (!t.isBlank() && !t.trim().equalsIgnoreCase(nombreArchivo.trim())) {
                    lista.add(t.trim());
                }
            }
            asigActual.setTemas(String.join(",", lista));
            asignaturaRepositorio.save(asigActual);
        }
        try {
            Filter filtro = metadataKey("asignatura_id").isEqualTo(asignaturaId)
                    .and(metadataKey("tema").isEqualTo(nombreArchivo));
            embeddingStore.removeAll(filtro);
        } catch (Exception e) {
            log.warn("No se pudo eliminar del almacén de vectores por filtro: {}", e.getMessage());
        }
        return true;
    }

    private int ingerirApuntes(String asignaturaId, MultipartFile[] archivos) {
        EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                .documentSplitter(DocumentSplitters.recursive(500, 50))
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .build();

        List<Document> documentos = new ArrayList<>();
        ApachePdfBoxDocumentParser pdfParser = new ApachePdfBoxDocumentParser();

        for (MultipartFile archivo : archivos) {
            if (archivo.isEmpty()) {
                continue;
            }
            try (InputStream entrada = archivo.getInputStream()) {
                Document documento = pdfParser.parse(entrada);
                documento.metadata().put("asignatura_id", asignaturaId);
                documento.metadata().put("tema", archivo.getOriginalFilename());
                documento.metadata().put("username", AUTOR_PROFESOR);
                documentos.add(documento);
                log.info("PDF subido para asignatura {}: {}", asignaturaId, archivo.getOriginalFilename());
            } catch (Exception e) {
                log.error("Error procesando PDF subido: {}", archivo.getOriginalFilename(), e);
            }
        }

        if (!documentos.isEmpty()) {
            ingestor.ingest(documentos);
        }
        return documentos.size();
    }

    
    public void ingerirTextoApunteAlumno(String asignaturaId, String username, String tema, String contenido) {
        if (contenido == null || contenido.isBlank()) {
            return;
        }
        String autor = (username == null || username.isBlank()) ? AUTOR_PROFESOR : username;
        String asig = (asignaturaId == null || asignaturaId.isBlank()) ? "General" : asignaturaId;
        String temaSeg = (tema == null || tema.isBlank()) ? "Apuntes propios" : tema;

        EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                .documentSplitter(DocumentSplitters.recursive(500, 50))
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .build();

        Document documento = Document.from(contenido);
        documento.metadata().put("asignatura_id", asig);
        documento.metadata().put("tema", temaSeg);
        documento.metadata().put("username", autor);

        ingestor.ingest(documento);
        log.info("Apuntes privados indexados para {} en asignatura {} (tema '{}')", autor, asig, temaSeg);
    }

    public boolean borrarApunteAlumno(String asignaturaId, String username, String tema) {
        try {
            Filter filtro = metadataKey("asignatura_id").isEqualTo(asignaturaId)
                    .and(metadataKey("username").isEqualTo(username))
                    .and(metadataKey("tema").isEqualTo(tema));
            embeddingStore.removeAll(filtro);
            return true;
        } catch (Exception e) {
            log.warn("No se pudo eliminar el apunte de alumno del almacén de vectores: {}", e.getMessage());
        }
        return false;
    }
}
