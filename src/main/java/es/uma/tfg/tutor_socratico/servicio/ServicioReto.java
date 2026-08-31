package es.uma.tfg.tutor_socratico.servicio;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
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

import es.uma.tfg.tutor_socratico.dto.ArchivoCodigo;
import es.uma.tfg.tutor_socratico.dto.Mensaje;
import es.uma.tfg.tutor_socratico.dto.MicrohitoDTO;
import es.uma.tfg.tutor_socratico.dto.PeticionChatReto;
import es.uma.tfg.tutor_socratico.dto.PeticionCrearEjercicioIa;
import es.uma.tfg.tutor_socratico.dto.PeticionPublicarEjercicio;
import es.uma.tfg.tutor_socratico.dto.PeticionRecargar;
import es.uma.tfg.tutor_socratico.dto.PeticionSubirEjercicio;
import es.uma.tfg.tutor_socratico.dto.AlumnoRadarDTO;
import es.uma.tfg.tutor_socratico.dto.DetalleEjercicioDTO;
import es.uma.tfg.tutor_socratico.dto.EjercicioRadarDTO;
import es.uma.tfg.tutor_socratico.dto.RespuestaChatReto;
import es.uma.tfg.tutor_socratico.dto.RespuestaEstadoReto;
import es.uma.tfg.tutor_socratico.dto.RespuestaPublicacion;
import es.uma.tfg.tutor_socratico.dto.RetoPropuestoResumen;
import es.uma.tfg.tutor_socratico.excepcion.RecursoNoEncontradoException;
import es.uma.tfg.tutor_socratico.persistencia.AvisoEstancamiento;
import es.uma.tfg.tutor_socratico.persistencia.EjercicioRepositorio;
import es.uma.tfg.tutor_socratico.persistencia.Ejercicio;
import es.uma.tfg.tutor_socratico.persistencia.EstadoMicrohito;
import es.uma.tfg.tutor_socratico.persistencia.Microhito;
import es.uma.tfg.tutor_socratico.persistencia.RegistroResolucion;
import es.uma.tfg.tutor_socratico.persistencia.RegistroResolucionRepositorio;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;


@Slf4j
@Service
public class ServicioReto {
    private static final int MAX_CHARS_CODIGO = 30_000;
    private static final int MAX_CHARS_CODIGO_TUTOR = 40_000;

    private static final int[] AUTONOMIA_BASE = { 100, 70, 45, 20 };

    private static final int PENALIZACION_POR_AYUDA_EXTRA = 5;

    private static final int NIVEL_AYUDA_CODIGO = 3;

    private static final int MIN_LONGITUD_LINEA_AUTORIA = 6;

    private static final String PROMPT_PROPONER_HITOS = """
            Eres un profesor de programación que descompone ejercicios en pasos de aprendizaje.
            Dado un enunciado, propón entre 3 y 6 microhitos ordenados y progresivos que un alumno
            debería completar para resolverlo (ej: 1. Definir la estructura de datos, 2. Reservar
            memoria, 3. Implementar la lógica, 4. Liberar recursos).
            Responde exclusivamente con un array JSON válido, sin markdown ni texto adicional:
            [{"titulo":"<breve>","descripcion":"<qué debe lograr el alumno>","criterioValidacion":"<cómo saber si el código lo cumple>"}]
            """;

    private static final String PROMPT_EVALUACION_HITOS = """
            Eres un evaluador pedagógico automático de ejercicios de programación. NO eres un tutor
            conversacional: en esta tarea NO das explicaciones, NO das pistas y NO te diriges al alumno.
            Tu única función es analizar el código actual del alumno contra una lista de microhitos y
            devolver una evaluación estructurada.

            ## Reglas de evaluación de cada microhito
            Asigna un estado:
            - "COMPLETADO": el código cumple de forma verificable el criterio de validación.
            - "EN_PROGRESO": hay un intento identificable pero incompleto o incorrecto.
            - "PENDIENTE": no hay rastro de ese hito en el código.
            Evalúa SOLO lo que ves en el código. Si el código está vacío para un hito, NO puede estar COMPLETADO.
            No estimes autonomía ni autoría: de eso se encarga el sistema con otras señales. Tu tarea es
            únicamente el ESTADO de cada microhito y un breve diagnóstico.

            ## Formato de salida OBLIGATORIO
            Responde EXCLUSIVAMENTE con un objeto JSON válido, sin markdown, sin ```json, sin texto extra:
            {
              "microhitos": [
                { "id": <long>, "estado": "COMPLETADO|EN_PROGRESO|PENDIENTE",
                  "evidencia": "<frase muy breve>" }
              ],
              "comentarioDocente": "<1-2 frases de diagnóstico>"
            }
            Si el código está vacío o ilegible, devuelve todos los hitos en PENDIENTE.
            """;

    private static final String PROMPT_CLASIFICAR_AYUDA = """
            Eres un clasificador. Recibes UNA respuesta que un tutor socrático dio a un alumno de
            programación. Clasifica el NIVEL DE AYUDA que esa respuesta proporciona, según esta escala:
              0 = solo hace preguntas o reflexiona, sin explicar ni resolver nada.
              1 = explica un concepto o teoría (definiciones, el porqué), sin decir cómo implementarlo.
              2 = da una pista estratégica o direccional sobre el siguiente paso, sin escribir código.
              3 = incluye pseudocódigo, un fragmento de código o la estructura concreta de la solución.
            Responde EXCLUSIVAMENTE con un único dígito: 0, 1, 2 o 3.
            """;

    private static final String PROMPT_SISTEMA_TUTOR_RETO =
            "Eres un tutor socrático universitario acompañando a un alumno mientras resuelve un ejercicio "
            + "de programación por microhitos. Guíale con preguntas y pistas conceptuales hacia el microhito "
            + "en el que está trabajando. NUNCA le escribas la solución completa ni el código final: como mucho "
            + "pseudocódigo parcial o un fragmento mínimo. Tu meta es que aprenda a resolverlo por sí mismo.";

    private final ChatLanguageModel chatLanguageModel;
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EjercicioRepositorio ejercicioRepositorio;
    private final RegistroResolucionRepositorio resolucionRepositorio;
    private final ServicioEstancamiento servicioEstancamiento;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ServicioReto(ChatLanguageModel chatLanguageModel,
                        EmbeddingModel embeddingModel,
                        EmbeddingStore<TextSegment> embeddingStore,
                        EjercicioRepositorio ejercicioRepositorio,
                        RegistroResolucionRepositorio resolucionRepositorio,
                        ServicioEstancamiento servicioEstancamiento) {
        this.chatLanguageModel = chatLanguageModel;
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.ejercicioRepositorio = ejercicioRepositorio;
        this.resolucionRepositorio = resolucionRepositorio;
        this.servicioEstancamiento = servicioEstancamiento;
    }


    @Transactional(readOnly = true)
    public List<RetoPropuestoResumen> listarPropuestos(String username, String asignaturaId) {
        String asig = normalizarAsignatura(asignaturaId);
        List<Ejercicio> propuestos = ejercicioRepositorio.findByAsignaturaIdAndPublicadoTrueOrderByFechaCreacionDesc(asig);
        List<RetoPropuestoResumen> salida = new ArrayList<>();
        for (Ejercicio e : propuestos) {
            boolean completado = resolucionRepositorio
                    .findFirstByUsernameAndEjercicioIdOrderByFechaInicioDesc(username, e.getId())
                    .map(r -> r.getEstado() == RegistroResolucion.Estado.COMPLETADO)
                    .orElse(false);
            salida.add(new RetoPropuestoResumen(
                    e.getId(),
                    e.getTitulo(),
                    e.getDificultad(),
                    e.getTema(),
                    e.getMicrohitos().size(),
                    completado
            ));
        }
        return salida;
    }

    @Transactional
    public DetalleEjercicioDTO crearEjercicioIa(PeticionCrearEjercicioIa peticion, String username, String asignaturaId) {
        String asig = normalizarAsignatura(asignaturaId);
        String contexto = contextoRag("ejercicios conceptos teoria ejemplos", peticion.tema(), asig, 5, username);

        String instruccion = String.format(
                "Con base en este material del temario:%n%s%n%n" +
                "Diseña el ENUNCIADO de un ejercicio práctico de programación de dificultad %s sobre \"%s\". " +
                "Devuelve solo el enunciado (sin resolverlo). Debe requerir escribir código.",
                contexto, peticion.dificultad(), peticion.tema());

        String enunciado = generarTexto("Eres un profesor universitario diseñando ejercicios de programación.", instruccion,
                "⚠️ No se ha podido generar el ejercicio en este momento.");

        String lenguaje = (peticion.lenguaje() == null || peticion.lenguaje().isBlank()) ? "java" : peticion.lenguaje();
        String titulo = "Ejercicio IA · " + peticion.tema() + " (" + peticion.dificultad() + ")";

        Ejercicio ejercicio = construirEjercicio(titulo, enunciado, Ejercicio.Origen.GENERADO_IA,
                peticion.dificultad(), peticion.tema(), asig, lenguaje, username, false);
        proponerMicrohitosConIa(enunciado, lenguaje).forEach(ejercicio::agregarMicrohito);
        ejercicioRepositorio.save(ejercicio);

        return detalleEjercicio(ejercicio);
    }

    @Transactional
    public DetalleEjercicioDTO subirEjercicio(PeticionSubirEjercicio peticion, String username, String asignaturaId) {
        String asig = normalizarAsignatura(asignaturaId);
        String lenguaje = (peticion.lenguaje() == null || peticion.lenguaje().isBlank()) ? "java" : peticion.lenguaje();

        Ejercicio ejercicio = construirEjercicio(peticion.titulo(), peticion.enunciado(), Ejercicio.Origen.PROPIO,
                "Propio", peticion.tema(), asig, lenguaje, username, false);
        proponerMicrohitosConIa(peticion.enunciado(), lenguaje).forEach(ejercicio::agregarMicrohito);
        ejercicioRepositorio.save(ejercicio);

        return detalleEjercicio(ejercicio);
    }

    @Transactional
    public RespuestaPublicacion publicarEjercicioPropuesto(PeticionPublicarEjercicio peticion, String username, String asignaturaId) {
        String asig = normalizarAsignatura(asignaturaId);
        String lenguaje = (peticion.lenguaje() == null || peticion.lenguaje().isBlank()) ? "java" : peticion.lenguaje();

        Ejercicio ejercicio = construirEjercicio(peticion.titulo(), peticion.enunciado(), Ejercicio.Origen.PROPUESTO,
                peticion.dificultad() != null ? peticion.dificultad() : "Media", peticion.tema(), asig, lenguaje, username, true);

        int orden = 1;
        for (MicrohitoDTO dto : peticion.microhitos()) {
            if (dto.titulo() == null || dto.titulo().isBlank()) continue;
            ejercicio.agregarMicrohito(Microhito.builder()
                    .orden(orden++)
                    .titulo(dto.titulo())
                    .descripcion(dto.descripcion())
                    .criterioValidacion(dto.criterioValidacion())
                    .build());
        }
        ejercicioRepositorio.save(ejercicio);

        return new RespuestaPublicacion(true, ejercicio.getId(), "Ejercicio publicado en 'Ejercicios Propuestos'.");
    }


    @Transactional
    public RespuestaEstadoReto iniciarResolucion(Long ejercicioId, String username, String asignaturaId) {
        Ejercicio ejercicio = ejercicioRepositorio.findById(ejercicioId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Ejercicio no encontrado: " + ejercicioId));
        String asig = normalizarAsignatura(asignaturaId);

        RegistroResolucion resolucion = RegistroResolucion.builder()
                .username(username)
                .ejercicioId(ejercicio.getId())
                .asignaturaId(asig)
                .estado(RegistroResolucion.Estado.EN_PROGRESO)
                .porcentajeIndependencia(100)
                .fechaInicio(LocalDateTime.now())
                .build();

        for (Microhito h : ejercicio.getMicrohitos()) {
            resolucion.agregarEstadoHito(EstadoMicrohito.builder()
                    .microhitoId(h.getId())
                    .orden(h.getOrden())
                    .titulo(h.getTitulo())
                    .estado(EstadoMicrohito.Estado.PENDIENTE)
                    .independenciaHito(100)
                    .build());
        }
        resolucionRepositorio.save(resolucion);

        return new RespuestaEstadoReto(
                resolucion.getId(),
                ejercicio.getId(),
                ejercicio.getTitulo(),
                ejercicio.getEnunciado(),
                ejercicio.getLenguaje(),
                100,
                100,
                100,
                false,
                "",
                estadosADTO(resolucion)
        );
    }

    @Transactional
    public RespuestaChatReto chatReto(PeticionChatReto peticion, String username, String asignaturaId) {
        RegistroResolucion resolucion = cargarResolucionPropia(peticion.resolucionId(), username);
        Ejercicio ejercicio = ejercicioRepositorio.findById(resolucion.getEjercicioId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Ejercicio no encontrado"));

        String ultimaPregunta = peticion.historial().get(peticion.historial().size() - 1).content();

        resolucion.setNMensajesAlumno(resolucion.getNMensajesAlumno() + 1);
        if (pareceBuscarSolucion(ultimaPregunta)) {
            resolucion.setNPistasReveladoras(resolucion.getNPistasReveladoras() + 1);
        }
        resolucionRepositorio.save(resolucion);

        String contexto = contextoRag(ultimaPregunta, ejercicio.getTema(), normalizarAsignatura(asignaturaId), 4, username);

        String textoSistema = PROMPT_SISTEMA_TUTOR_RETO + "\n\n" +
                ServicioTutor.DIRECTIVA_ANTI_INYECCION + "\n\n" +
                "## Enunciado del ejercicio\n" + ejercicio.getEnunciado() + "\n\n" +
                "## Microhitos y su estado actual\n" + descripcionHitos(resolucion) + "\n\n" +
                "## Contexto de los apuntes oficiales\n<<<DATOS>>>\n" + contexto + "\n<<<FIN_DATOS>>>\n\n" +
                "Céntrate en el primer microhito que no esté COMPLETADO. Guíale sin resolverlo por él.\n\n" +
                ServicioTutor.DIRECTIVA_DERIVACION_DOCENTE;

        List<ChatMessage> mensajes = new ArrayList<>();
        mensajes.add(SystemMessage.from(textoSistema));
        for (Mensaje m : peticion.historial()) {
            if (m == null || m.role() == null || m.content() == null) continue;
            if (m.role().equalsIgnoreCase("user")) mensajes.add(UserMessage.from(m.content()));
            else mensajes.add(AiMessage.from(m.content()));
        }

        String respuesta;
        try {
            Response<AiMessage> r = chatLanguageModel.generate(mensajes);
            respuesta = (r != null && r.content() != null) ? r.content().text()
                    : "⚠️ No se ha obtenido respuesta del tutor.";
        } catch (Throwable e) {
            log.error("Error en chatReto: {}", e.getMessage(), e);
            respuesta = "⚠️ **Aviso de IA:** No se ha podido obtener respuesta del tutor en este momento.";
        }


        registrarAyudaTutor(resolucion, respuesta);
        resolucionRepositorio.save(resolucion);

        ServicioEstancamiento.Evaluacion estanc = servicioEstancamiento.evaluar(
                username, normalizarAsignatura(asignaturaId), AvisoEstancamiento.Ambito.RETO,
                microhitoActivo(resolucion), ultimaPregunta);

        return new RespuestaChatReto(
                respuesta,
                estanc.estancado(),
                estanc.avisoId() != null ? estanc.avisoId() : 0L,
                estanc.estancado() ? estanc.mensaje() : null
        );
    }

    private String microhitoActivo(RegistroResolucion resolucion) {
        return microhitoActivoEstado(resolucion)
                .map(EstadoMicrohito::getTitulo)
                .orElse("Reto completado");
    }

    private Optional<EstadoMicrohito> microhitoActivoEstado(RegistroResolucion resolucion) {
        return resolucion.getEstadosHitos().stream()
                .filter(h -> h.getEstado() != EstadoMicrohito.Estado.COMPLETADO)
                .findFirst();
    }

    private void registrarAyudaTutor(RegistroResolucion resolucion, String respuestaTutor) {
        if (respuestaTutor == null || respuestaTutor.isBlank() || respuestaTutor.startsWith("⚠️")) {
            return;
        }
        List<String> bloques = extraerBloquesCodigo(respuestaTutor);
        int nivel;
        if (!bloques.isEmpty()) {
            nivel = NIVEL_AYUDA_CODIGO;                 // el tutor escribió código: nivel máximo
            acumularCodigoTutor(resolucion, bloques);
        } else {
            nivel = clasificarNivelAyuda(respuestaTutor);
        }
        if (nivel <= 0) {
            return;                                     // sin ayuda sustantiva: no afecta a la autonomía
        }
        microhitoActivoEstado(resolucion).ifPresent(h -> {
            int previo = h.getNivelAyudaMax() != null ? h.getNivelAyudaMax() : 0;
            h.setNivelAyudaMax(Math.max(previo, nivel));
            h.setNAyudas((h.getNAyudas() != null ? h.getNAyudas() : 0) + 1);
        });
    }


    private List<String> extraerBloquesCodigo(String texto) {
        List<String> bloques = new ArrayList<>();
        if (texto == null) return bloques;
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("```[\\w+#.-]*\\s*\\n(.*?)```", java.util.regex.Pattern.DOTALL)
                .matcher(texto);
        while (m.find()) {
            String cuerpo = m.group(1);
            if (cuerpo != null && !cuerpo.isBlank()) bloques.add(cuerpo);
        }
        return bloques;
    }

    private void acumularCodigoTutor(RegistroResolucion resolucion, List<String> bloques) {
        StringBuilder sb = new StringBuilder(
                resolucion.getCodigoTutorAcumulado() != null ? resolucion.getCodigoTutorAcumulado() : "");
        for (String b : bloques) {
            if (sb.length() >= MAX_CHARS_CODIGO_TUTOR) break;
            sb.append(b).append("\n");
        }
        if (sb.length() > MAX_CHARS_CODIGO_TUTOR) sb.setLength(MAX_CHARS_CODIGO_TUTOR);
        resolucion.setCodigoTutorAcumulado(sb.toString());
    }

    /** Clasifica el nivel de ayuda (0-3) de la respuesta del tutor con el LLM. Ante la duda, 0. */
    private int clasificarNivelAyuda(String respuestaTutor) {
        try {
            Response<AiMessage> r = chatLanguageModel.generate(
                    SystemMessage.from(PROMPT_CLASIFICAR_AYUDA),
                    UserMessage.from("Respuesta del tutor a clasificar:\n" + respuestaTutor));
            String txt = (r != null && r.content() != null) ? r.content().text() : "";
            for (int i = 0; i < txt.length(); i++) {
                char c = txt.charAt(i);
                if (c >= '0' && c <= '3') return c - '0';
            }
        } catch (Exception e) {
            log.warn("No se pudo clasificar el nivel de ayuda: {}", e.getMessage());
        }
        return 0;
    }


    @Transactional
    public RespuestaEstadoReto recargar(PeticionRecargar peticion, String username) {
        RegistroResolucion resolucion = cargarResolucionPropia(peticion.resolucionId(), username);
        Ejercicio ejercicio = ejercicioRepositorio.findById(resolucion.getEjercicioId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Ejercicio no encontrado"));

        resolucion.setNRecargas(resolucion.getNRecargas() + 1);

        String codigo = concatenarCodigo(peticion.archivos());
        String hitosJson = hitosParaEvaluacion(resolucion);

        String userMessage = "## ENUNCIADO\n" + ejercicio.getEnunciado() + "\n\n" +
                "## MICROHITOS (id, título, criterio)\n" + hitosJson + "\n\n" +
                "## CÓDIGO ACTUAL DEL ALUMNO\n" + (codigo.isBlank() ? "(vacío)" : codigo);

        String comentarioDocente = "";
        Map<Long, EstadoMicrohito> porId = new HashMap<>();
        for (EstadoMicrohito eh : resolucion.getEstadosHitos()) porId.put(eh.getMicrohitoId(), eh);

        try {
            Response<AiMessage> r = chatLanguageModel.generate(
                    SystemMessage.from(PROMPT_EVALUACION_HITOS), UserMessage.from(userMessage));
            String bruto = (r != null && r.content() != null) ? r.content().text() : "";
            JsonNode json = extraerJsonObjeto(bruto);
            if (json != null) {
                if (json.has("comentarioDocente")) comentarioDocente = json.get("comentarioDocente").asText("");
                JsonNode arr = json.get("microhitos");
                if (arr != null && arr.isArray()) {
                    for (JsonNode nodo : arr) {
                        long id = nodo.path("id").asLong(-1);
                        EstadoMicrohito eh = porId.get(id);
                        if (eh == null) continue;
                        eh.setEstado(parseEstadoHito(nodo.path("estado").asText("PENDIENTE")));
                        if (eh.getEstado() == EstadoMicrohito.Estado.COMPLETADO && eh.getFechaCompletado() == null) {
                            eh.setFechaCompletado(LocalDateTime.now());
                        }
                    }
                }
            } else {
                log.warn("La evaluación del LLM no devolvió JSON parseable para resolución {}", resolucion.getId());
            }
        } catch (Throwable e) {
            log.error("Error evaluando código en recargar: {}", e.getMessage(), e);
        }

        int sumaAutonomia = 0, nEvaluados = 0;
        for (EstadoMicrohito eh : resolucion.getEstadosHitos()) {
            if (eh.getEstado() == EstadoMicrohito.Estado.PENDIENTE) {
                eh.setIndependenciaHito(null);
                continue;
            }
            int autonomia = autonomiaHito(eh);
            eh.setIndependenciaHito(autonomia);
            if (eh.getEstado() == EstadoMicrohito.Estado.COMPLETADO || eh.getEstado() == EstadoMicrohito.Estado.EN_PROGRESO) {
                sumaAutonomia += autonomia;
                nEvaluados++;
            }
        }
        int autonomiaGlobal = nEvaluados > 0 ? Math.round((float) sumaAutonomia / nEvaluados) : 100;
        resolucion.setPorcentajeIndependencia(acotar(autonomiaGlobal));

        resolucion.setPorcentajeAutoria(calcularAutoria(resolucion.getCodigoTutorAcumulado(), codigo));

        boolean todosCompletados = !resolucion.getEstadosHitos().isEmpty() &&
                resolucion.getEstadosHitos().stream()
                        .allMatch(h -> h.getEstado() == EstadoMicrohito.Estado.COMPLETADO);
        if (todosCompletados && resolucion.getEstado() != RegistroResolucion.Estado.COMPLETADO) {
            resolucion.setEstado(RegistroResolucion.Estado.COMPLETADO);
            resolucion.setFechaFin(LocalDateTime.now());
            if (resolucion.getFechaInicio() != null) {
                resolucion.setTiempoTotalSegundos(
                        (int) Duration.between(resolucion.getFechaInicio(), resolucion.getFechaFin()).getSeconds());
            }
        }
        resolucionRepositorio.save(resolucion);

        return new RespuestaEstadoReto(
                resolucion.getId(),
                ejercicio.getId(),
                ejercicio.getTitulo(),
                ejercicio.getEnunciado(),
                ejercicio.getLenguaje(),
                resolucion.getPorcentajeIndependencia(),
                resolucion.getPorcentajeIndependencia(),
                resolucion.getPorcentajeAutoria(),
                todosCompletados,
                comentarioDocente,
                estadosADTO(resolucion)
        );
    }

    private int autonomiaHito(EstadoMicrohito eh) {
        int nivel = eh.getNivelAyudaMax() != null ? Math.max(0, Math.min(3, eh.getNivelAyudaMax())) : 0;
        int ayudas = eh.getNAyudas() != null ? Math.max(0, eh.getNAyudas()) : 0;
        int base = AUTONOMIA_BASE[nivel];
        int suelo = AUTONOMIA_BASE[Math.min(3, nivel + 1)];   // no cae por debajo del nivel siguiente
        int autonomia = base - PENALIZACION_POR_AYUDA_EXTRA * Math.max(0, ayudas - 1);
        return acotar(Math.max(suelo, autonomia));
    }


    private int calcularAutoria(String codigoTutor, String codigoAlumno) {
        if (codigoAlumno == null || codigoAlumno.isBlank()) return 100;
        if (codigoTutor == null || codigoTutor.isBlank()) return 100;

        java.util.Set<String> lineasTutor = new java.util.HashSet<>();
        for (String l : codigoTutor.split("\\r?\\n")) {
            String n = normalizarLinea(l);
            if (n.length() >= MIN_LONGITUD_LINEA_AUTORIA) lineasTutor.add(n);
        }
        if (lineasTutor.isEmpty()) return 100;

        int total = 0, copiadas = 0;
        for (String l : codigoAlumno.split("\\r?\\n")) {
            String n = normalizarLinea(l);
            if (n.length() < MIN_LONGITUD_LINEA_AUTORIA) continue;
            total++;
            if (lineasTutor.contains(n)) copiadas++;
        }
        if (total == 0) return 100;
        return acotar(Math.round(100f * (total - copiadas) / total));
    }

    private String normalizarLinea(String linea) {
        return linea == null ? "" : linea.trim().replaceAll("\\s+", " ");
    }

    @Transactional(readOnly = true)
    public List<EjercicioRadarDTO> radarDocente(String asignaturaId) {
        String asig = normalizarAsignatura(asignaturaId);
        List<Ejercicio> publicados = ejercicioRepositorio.findByAsignaturaIdAndPublicadoTrueOrderByFechaCreacionDesc(asig);
        List<RegistroResolucion> resoluciones = resolucionRepositorio.findByAsignaturaId(asig);

        List<EjercicioRadarDTO> salida = new ArrayList<>();
        for (Ejercicio e : publicados) {
            List<RegistroResolucion> deEste = resoluciones.stream()
                    .filter(r -> e.getId().equals(r.getEjercicioId()))
                    .toList();

            List<AlumnoRadarDTO> alumnos = new ArrayList<>();
            int sumaIndep = 0, nConIndep = 0, nCompletados = 0, sumaAutoria = 0, nConAutoria = 0;
            for (RegistroResolucion r : deEste) {
                boolean completado = r.getEstado() == RegistroResolucion.Estado.COMPLETADO;
                if (completado) nCompletados++;
                Integer indep = r.getPorcentajeIndependencia();
                if (indep != null) { sumaIndep += indep; nConIndep++; }
                Integer autoria = r.getPorcentajeAutoria();
                if (autoria != null) { sumaAutoria += autoria; nConAutoria++; }
                
                alumnos.add(new AlumnoRadarDTO(
                        r.getUsername(),
                        r.getEstado().name(),
                        indep,
                        indep,
                        autoria,
                        r.getTiempoTotalSegundos(),
                        r.getFechaFin() != null ? r.getFechaFin().toString() : null
                ));
            }

            salida.add(new EjercicioRadarDTO(
                    e.getId(),
                    e.getTitulo(),
                    e.getDificultad(),
                    deEste.size(),
                    nCompletados,
                    nConIndep > 0 ? Math.round((float) sumaIndep / nConIndep) : null,
                    nConIndep > 0 ? Math.round((float) sumaIndep / nConIndep) : null,
                    nConAutoria > 0 ? Math.round((float) sumaAutoria / nConAutoria) : null,
                    alumnos
            ));
        }
        return salida;
    }


    private Ejercicio construirEjercicio(String titulo, String enunciado, Ejercicio.Origen origen,
                                         String dificultad, String tema, String asignaturaId,
                                         String lenguaje, String autor, boolean publicado) {
        return Ejercicio.builder()
                .titulo(titulo)
                .enunciado(enunciado)
                .origen(origen)
                .dificultad(dificultad)
                .tema(tema)
                .asignaturaId(asignaturaId)
                .lenguaje(lenguaje)
                .autorUsername(autor)
                .publicado(publicado)
                .fechaCreacion(LocalDateTime.now())
                .build();
    }

    private DetalleEjercicioDTO detalleEjercicio(Ejercicio ejercicio) {
        List<MicrohitoDTO> hitos = new ArrayList<>();
        for (Microhito h : ejercicio.getMicrohitos()) {
            hitos.add(new MicrohitoDTO(h.getId(), h.getOrden(), h.getTitulo(),
                    h.getDescripcion(), h.getCriterioValidacion(), null, null));
        }
        return new DetalleEjercicioDTO(
                ejercicio.getId(),
                ejercicio.getTitulo(),
                ejercicio.getEnunciado(),
                ejercicio.getLenguaje(),
                hitos
        );
    }

    private List<MicrohitoDTO> estadosADTO(RegistroResolucion resolucion) {
        List<MicrohitoDTO> hitos = new ArrayList<>();
        for (EstadoMicrohito h : resolucion.getEstadosHitos()) {
            hitos.add(new MicrohitoDTO(h.getMicrohitoId(), h.getOrden(), h.getTitulo(),
                    null, null, h.getEstado().name(), h.getIndependenciaHito()));
        }
        return hitos;
    }

    private RegistroResolucion cargarResolucionPropia(Long resolucionId, String username) {
        RegistroResolucion resolucion = resolucionRepositorio.findById(resolucionId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Resolución no encontrada: " + resolucionId));
        if (!resolucion.getUsername().equals(username)) {
            
            throw new RecursoNoEncontradoException("Resolución no encontrada: " + resolucionId);
        }
        return resolucion;
    }

    private List<Microhito> proponerMicrohitosConIa(String enunciado, String lenguaje) {
        List<Microhito> hitos = new ArrayList<>();
        try {
            Response<AiMessage> r = chatLanguageModel.generate(
                    SystemMessage.from(PROMPT_PROPONER_HITOS),
                    UserMessage.from("Lenguaje: " + lenguaje + "\n\nEnunciado:\n" + enunciado));
            String bruto = (r != null && r.content() != null) ? r.content().text() : "";
            JsonNode arr = extraerJsonArray(bruto);
            if (arr != null && arr.isArray()) {
                int orden = 1;
                for (JsonNode nodo : arr) {
                    String titulo = nodo.path("titulo").asText("");
                    if (titulo.isBlank()) continue;
                    hitos.add(Microhito.builder()
                            .orden(orden++)
                            .titulo(titulo)
                            .descripcion(nodo.path("descripcion").asText(""))
                            .criterioValidacion(nodo.path("criterioValidacion").asText(""))
                            .build());
                }
            }
        } catch (Exception e) {
            log.error("Error proponiendo microhitos con IA: {}", e.getMessage(), e);
        }
        if (hitos.isEmpty()) {
            
            hitos.add(Microhito.builder().orden(1).titulo("Analizar el enunciado y diseñar la solución")
                    .descripcion("Identifica qué se pide y esboza el enfoque.").criterioValidacion("Existe estructura inicial del código.").build());
            hitos.add(Microhito.builder().orden(2).titulo("Implementar la solución")
                    .descripcion("Escribe el código que resuelve el problema.").criterioValidacion("La lógica principal está implementada.").build());
            hitos.add(Microhito.builder().orden(3).titulo("Probar y refinar")
                    .descripcion("Verifica el resultado y corrige errores.").criterioValidacion("El código produce el resultado esperado.").build());
        }
        return hitos;
    }

    private String contextoRag(String consulta, String tema, String asignaturaId, int maxResultados, String usuarioActual) {
        StringBuilder contexto = new StringBuilder();
        for (EmbeddingMatch<TextSegment> m : buscarSegmentosRelevantes(consulta, tema, asignaturaId, maxResultados, usuarioActual)) {
            if (m != null && m.embedded() != null && m.embedded().text() != null) {
                contexto.append(m.embedded().text()).append("\n\n");
            }
        }
        return contexto.toString();
    }

    private List<EmbeddingMatch<TextSegment>> buscarSegmentosRelevantes(String textoConsulta, String tema,
                                                                        String asignaturaId, int maxResultados,
                                                                        String usuarioActual) {
        try {
            Embedding vector = embeddingModel.embed(textoConsulta).content();
            var builder = EmbeddingSearchRequest.builder().queryEmbedding(vector).maxResults(maxResultados);
            Filter filtro = null;
            if (asignaturaId != null && !asignaturaId.isBlank()) {
                filtro = metadataKey("asignatura_id").isEqualTo(asignaturaId);
            }
            if (tema != null && !tema.trim().isEmpty() && !tema.equalsIgnoreCase("General")) {
                Filter filtroTema = metadataKey("tema").isEqualTo(tema);
                filtro = (filtro == null) ? filtroTema : filtro.and(filtroTema);
            }
            
            Filter filtroAutor = (usuarioActual != null && !usuarioActual.isBlank())
                    ? metadataKey("username").isIn(ServicioIngesta.AUTOR_PROFESOR, usuarioActual)
                    : metadataKey("username").isEqualTo(ServicioIngesta.AUTOR_PROFESOR);
            filtro = (filtro == null) ? filtroAutor : filtro.and(filtroAutor);
            builder.filter(filtro);
            return embeddingStore.search(builder.build()).matches();
        } catch (Exception t) {
            log.warn("No se pudo buscar embeddings para '{}': {}", textoConsulta, t.getMessage());
            return java.util.Collections.emptyList();
        }
    }

    private String generarTexto(String sistema, String instruccion, String fallback) {
        try {
            Response<AiMessage> r = chatLanguageModel.generate(SystemMessage.from(sistema), UserMessage.from(instruccion));
            return (r != null && r.content() != null) ? r.content().text() : fallback;
        } catch (Exception e) {
            log.error("Error generando texto con IA: {}", e.getMessage(), e);
            return fallback;
        }
    }

    private String descripcionHitos(RegistroResolucion resolucion) {
        StringBuilder sb = new StringBuilder();
        for (EstadoMicrohito h : resolucion.getEstadosHitos()) {
            sb.append(h.getOrden()).append(". ").append(h.getTitulo())
              .append(" [").append(h.getEstado().name()).append("]\n");
        }
        return sb.toString();
    }

    private String hitosParaEvaluacion(RegistroResolucion resolucion) {
        
        Ejercicio ejercicio = ejercicioRepositorio.findById(resolucion.getEjercicioId()).orElse(null);
        Map<Long, String> criterios = new HashMap<>();
        if (ejercicio != null) {
            for (Microhito h : ejercicio.getMicrohitos()) criterios.put(h.getId(), h.getCriterioValidacion());
        }
        StringBuilder sb = new StringBuilder();
        for (EstadoMicrohito h : resolucion.getEstadosHitos()) {
            sb.append("- id=").append(h.getMicrohitoId())
              .append(" | ").append(h.getTitulo())
              .append(" | criterio: ").append(Optional.ofNullable(criterios.get(h.getMicrohitoId())).orElse("(sin criterio)"))
              .append("\n");
        }
        return sb.toString();
    }

    private String concatenarCodigo(List<ArchivoCodigo> archivos) {
        if (archivos == null || archivos.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (ArchivoCodigo a : archivos) {
            if (a == null || a.contenido() == null) continue;
            sb.append("// ===== ").append(a.ruta() != null ? a.ruta() : "archivo").append(" =====\n");
            sb.append(a.contenido()).append("\n\n");
            if (sb.length() > MAX_CHARS_CODIGO) {
                sb.setLength(MAX_CHARS_CODIGO);
                sb.append("\n// ... (código truncado por longitud) ...");
                break;
            }
        }
        return sb.toString();
    }

    private boolean pareceBuscarSolucion(String texto) {
        if (texto == null) return false;
        String t = texto.toLowerCase(Locale.ROOT);
        return t.contains("solucion") || t.contains("solución") || t.contains("dame el codigo")
                || t.contains("dame el código") || t.contains("resuelvelo") || t.contains("resuélvelo")
                || t.contains("me rindo") || t.contains("no se hacerlo") || t.contains("no sé hacerlo")
                || t.contains("escribeme") || t.contains("escríbeme") || t.contains("completa el codigo")
                || t.contains("hazme");
    }

    private JsonNode extraerJsonObjeto(String bruto) {
        if (bruto == null) return null;
        int ini = bruto.indexOf('{');
        int fin = bruto.lastIndexOf('}');
        if (ini < 0 || fin <= ini) return null;
        try {
            return objectMapper.readTree(bruto.substring(ini, fin + 1));
        } catch (Exception e) {
            return null;
        }
    }

    private JsonNode extraerJsonArray(String bruto) {
        if (bruto == null) return null;
        int ini = bruto.indexOf('[');
        int fin = bruto.lastIndexOf(']');
        if (ini < 0 || fin <= ini) return null;
        try {
            return objectMapper.readTree(bruto.substring(ini, fin + 1));
        } catch (Exception e) {
            return null;
        }
    }

    private EstadoMicrohito.Estado parseEstadoHito(String valor) {
        if (valor == null) return EstadoMicrohito.Estado.PENDIENTE;
        String v = valor.trim().toUpperCase(Locale.ROOT);
        try {
            return EstadoMicrohito.Estado.valueOf(v);
        } catch (IllegalArgumentException e) {
            return EstadoMicrohito.Estado.PENDIENTE;
        }
    }

    private int acotar(int valor) {
        return Math.max(0, Math.min(100, valor));
    }

    private String normalizarAsignatura(String asignaturaId) {
        return (asignaturaId == null || asignaturaId.isBlank()) ? "General" : asignaturaId;
    }
}
