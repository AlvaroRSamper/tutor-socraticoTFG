package es.uma.tfg.tutor_socratico.servicio;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import es.uma.tfg.tutor_socratico.persistencia.Asignatura;
import es.uma.tfg.tutor_socratico.persistencia.AsignaturaRepositorio;
import es.uma.tfg.tutor_socratico.persistencia.AvisoEstancamiento;
import es.uma.tfg.tutor_socratico.persistencia.AvisoEstancamiento.Ambito;
import es.uma.tfg.tutor_socratico.persistencia.AvisoEstancamiento.Estado;
import es.uma.tfg.tutor_socratico.persistencia.AvisoEstancamientoRepositorio;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class ServicioEstancamiento {

    private static final int SENSIBILIDAD_MIN = 4;
    private static final int SENSIBILIDAD_MAX = 8;
    private static final int SENSIBILIDAD_DEFECTO = 5;

    private static final String PROMPT_CLASIFICADOR =
            "Eres un clasificador de intención de estudio. Recibes dos preguntas de un mismo alumno. " +
            "Decide si tratan esencialmente la MISMA duda o concepto concreto (aunque estén redactadas distinto), " +
            "o si son dudas DIFERENTES. Responde con una sola palabra: SI (misma duda) o NO (duda diferente).";

    private static final class Racha {
        String tema;
        String preguntaAncla;
        int contador;
    }

    private final Map<String, Racha> rachas = new ConcurrentHashMap<>();
    private final ChatLanguageModel chatLanguageModel;
    private final AsignaturaRepositorio asignaturaRepositorio;
    private final AvisoEstancamientoRepositorio avisoRepositorio;

    public ServicioEstancamiento(ChatLanguageModel chatLanguageModel,
                                 AsignaturaRepositorio asignaturaRepositorio,
                                 AvisoEstancamientoRepositorio avisoRepositorio) {
        this.chatLanguageModel = chatLanguageModel;
        this.asignaturaRepositorio = asignaturaRepositorio;
        this.avisoRepositorio = avisoRepositorio;
    }

    public record Evaluacion(boolean estancado, Long avisoId, int iteraciones, int umbral, String mensaje) {
        static Evaluacion sin() { return new Evaluacion(false, null, 0, 0, null); }
    }

    public Evaluacion evaluar(String username, String asignaturaId, Ambito ambito, String tema, String pregunta) {
        try {
            String asig = normalizar(asignaturaId);
            int umbral = obtenerSensibilidad(asig);
            String temaNorm = (tema == null || tema.isBlank()) ? "General" : tema.trim();
            String clave = username + "|" + asig + "|" + ambito;

            Racha racha = rachas.computeIfAbsent(clave, k -> new Racha());
            synchronized (racha) {
                boolean mismoTema = temaNorm.equalsIgnoreCase(racha.tema);
                if (!mismoTema) {
                    racha.tema = temaNorm;
                    racha.preguntaAncla = pregunta;
                    racha.contador = 1;
                } else if (esMismaDuda(racha.preguntaAncla, pregunta)) {
                    racha.contador++;
                } else {
                    racha.preguntaAncla = pregunta;
                    racha.contador = 1;
                }

                if (racha.contador >= umbral) {
                    Long avisoId = registrarOActualizarAviso(username, asig, ambito, temaNorm, racha.preguntaAncla, racha.contador);
                    return new Evaluacion(true, avisoId, racha.contador, umbral,
                            "Oye, estoy viendo que estás estancado, ¿por qué no avisas por tutoría a tu profesor?");
                }
                return new Evaluacion(false, null, racha.contador, umbral, null);
            }
        } catch (Throwable t) {
            log.warn("Fallo evaluando estancamiento de {}: {}", username, t.getMessage());
            return Evaluacion.sin();
        }
    }

    public void reiniciarRacha(String username, String asignaturaId, Ambito ambito) {
        rachas.remove(username + "|" + normalizar(asignaturaId) + "|" + ambito);
    }

    public List<Map<String, Object>> listarAlumnosConProblemas(String asignaturaId) {
        String asig = normalizar(asignaturaId);
        return avisoRepositorio.findByAsignaturaIdAndEstadoOrderByFechaActualizacionDesc(asig, Estado.ABIERTO)
                .stream()
                .map(a -> Map.<String, Object>of(
                        "avisoId", a.getId(),
                        "alumno", a.getUsername(),
                        "asignaturaId", a.getAsignaturaId() != null ? a.getAsignaturaId() : "General",
                        "ambito", a.getAmbito().name(),
                        "tema", a.getTema() != null ? a.getTema() : "—",
                        "preguntaEjemplo", a.getPreguntaEjemplo() != null ? a.getPreguntaEjemplo() : "",
                        "iteraciones", a.getIteraciones(),
                        "fecha", (a.getFechaActualizacion() != null ? a.getFechaActualizacion() : a.getFechaCreacion()).toString()))
                .toList();
    }

    public boolean resolverAviso(Long avisoId) {
        if (avisoId == null) return false;
        return avisoRepositorio.findById(avisoId).map(a -> {
            a.setEstado(Estado.RESUELTO);
            a.setFechaResuelto(LocalDateTime.now());
            avisoRepositorio.save(a);
            return true;
        }).orElse(false);
    }

    public int obtenerSensibilidad(String asignaturaId) {
        return asignaturaRepositorio.findById(normalizar(asignaturaId))
                .map(Asignatura::getSensibilidad)
                .map(this::acotarSensibilidad)
                .orElse(SENSIBILIDAD_DEFECTO);
    }

    public int guardarSensibilidad(String asignaturaId, int valor) {
        String asig = normalizar(asignaturaId);
        int v = acotarSensibilidad(valor);
        Asignatura a = asignaturaRepositorio.findById(asig)
                .orElseGet(() -> Asignatura.builder().asignaturaId(asig).build());
        a.setSensibilidad(v);
        asignaturaRepositorio.save(a);
        return v;
    }

    private Long registrarOActualizarAviso(String username, String asignaturaId, Ambito ambito,
                                           String tema, String preguntaEjemplo, int iteraciones) {
        AvisoEstancamiento aviso = avisoRepositorio
                .findFirstByUsernameAndAsignaturaIdAndAmbitoAndTemaAndEstado(username, asignaturaId, ambito, tema, Estado.ABIERTO)
                .orElseGet(() -> AvisoEstancamiento.builder()
                        .username(username)
                        .asignaturaId(asignaturaId)
                        .ambito(ambito)
                        .tema(tema)
                        .estado(Estado.ABIERTO)
                        .fechaCreacion(LocalDateTime.now())
                        .build());
        aviso.setPreguntaEjemplo(preguntaEjemplo);
        aviso.setIteraciones(iteraciones);
        aviso.setFechaActualizacion(LocalDateTime.now());
        return avisoRepositorio.save(aviso).getId();
    }


    private boolean esMismaDuda(String ancla, String nueva) {
        if (ancla == null || nueva == null || ancla.isBlank() || nueva.isBlank()) return false;
        if (ancla.trim().equalsIgnoreCase(nueva.trim())) return true;
        try {
            Response<AiMessage> r = chatLanguageModel.generate(
                    SystemMessage.from(PROMPT_CLASIFICADOR),
                    UserMessage.from("Pregunta A (ancla): " + ancla + "\nPregunta B (nueva): " + nueva +
                            "\n¿Misma duda o concepto concreto? Responde SI o NO."));
            String txt = (r != null && r.content() != null) ? r.content().text() : "";
            String t = txt.trim().toUpperCase(Locale.ROOT);
            return t.startsWith("SI") || t.startsWith("SÍ") || t.contains(" SI") || t.contains("MISMA");
        } catch (Throwable e) {
            log.warn("No se pudo clasificar la duda con el LLM: {}", e.getMessage());
            return false;
        }
    }

    private int acotarSensibilidad(Integer valor) {
        if (valor == null) return SENSIBILIDAD_DEFECTO;
        return Math.max(SENSIBILIDAD_MIN, Math.min(SENSIBILIDAD_MAX, valor));
    }

    private String normalizar(String asignaturaId) {
        return (asignaturaId == null || asignaturaId.isBlank()) ? "General" : asignaturaId;
    }
}
