package es.uma.tfg.tutor_socratico.controlador;

import es.uma.tfg.tutor_socratico.dto.ConsultaDTO;
import es.uma.tfg.tutor_socratico.dto.PerfilDocente;
import es.uma.tfg.tutor_socratico.dto.RespuestaConfiguracion;
import es.uma.tfg.tutor_socratico.dto.RespuestaExito;
import es.uma.tfg.tutor_socratico.dto.RespuestaInfoAsignatura;
import es.uma.tfg.tutor_socratico.dto.RespuestaRadar;
import es.uma.tfg.tutor_socratico.dto.RespuestaResumen;
import es.uma.tfg.tutor_socratico.dto.RespuestaSensibilidad;
import es.uma.tfg.tutor_socratico.persistencia.Asignatura;
import es.uma.tfg.tutor_socratico.persistencia.AsignaturaRepositorio;
import es.uma.tfg.tutor_socratico.persistencia.PerfilAlumnoRegistro;
import es.uma.tfg.tutor_socratico.persistencia.PerfilAlumnoRepositorio;
import es.uma.tfg.tutor_socratico.persistencia.RegistroConsulta;
import es.uma.tfg.tutor_socratico.persistencia.RegistroConsultaRepositorio;
import es.uma.tfg.tutor_socratico.servicio.ServicioEstancamiento;
import es.uma.tfg.tutor_socratico.servicio.ServicioInformeSemanal;
import es.uma.tfg.tutor_socratico.servicio.ServicioIngesta;
import es.uma.tfg.tutor_socratico.servicio.ServicioTutor;
import jakarta.servlet.http.HttpSession;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/profesor")
public class ControladorProfesor {

    private final RegistroConsultaRepositorio repositorio;
    private final PerfilAlumnoRepositorio perfilRepositorio;
    private final ServicioIngesta servicioIngesta;
    private final AsignaturaRepositorio asignaturaRepositorio;
    private final ServicioTutor servicioTutor;
    private final ServicioEstancamiento servicioEstancamiento;
    private final ServicioInformeSemanal servicioInformeSemanal;

    public ControladorProfesor(RegistroConsultaRepositorio repositorio,
                               PerfilAlumnoRepositorio perfilRepositorio,
                               ServicioIngesta servicioIngesta,
                               AsignaturaRepositorio asignaturaRepositorio,
                               ServicioTutor servicioTutor,
                               ServicioEstancamiento servicioEstancamiento,
                               ServicioInformeSemanal servicioInformeSemanal) {
        this.repositorio = repositorio;
        this.perfilRepositorio = perfilRepositorio;
        this.servicioIngesta = servicioIngesta;
        this.asignaturaRepositorio = asignaturaRepositorio;
        this.servicioTutor = servicioTutor;
        this.servicioEstancamiento = servicioEstancamiento;
        this.servicioInformeSemanal = servicioInformeSemanal;
    }

    private String asignaturaDe(HttpSession session) {
        String asig = (String) session.getAttribute("asignatura_id");
        return (asig == null || asig.isBlank()) ? "General" : asig;
    }

    @GetMapping("/asignatura/info")
    public RespuestaInfoAsignatura obtenerInfoAsignatura(HttpSession session) {
        String asignaturaId = asignaturaDe(session);
        Asignatura asig = asignaturaRepositorio.findById(asignaturaId).orElse(null);
        String titulo = (asig != null && asig.getTitulo() != null) ? asig.getTitulo() : "Tutor Socrático";
        String prompt = (asig != null && asig.getSystemPrompt() != null) ? asig.getSystemPrompt() : "";
        String colorTema = (asig != null && asig.getColorTema() != null) ? asig.getColorTema() : "github-dark";
        String temas = (asig != null && asig.getTemas() != null) ? asig.getTemas() : "";
        int sensibilidad = servicioEstancamiento.obtenerSensibilidad(asignaturaId);
        String emailProfesor = (asig != null && asig.getEmailProfesor() != null) ? asig.getEmailProfesor() : "";
        Integer diaInformeSemanal = (asig != null) ? asig.getDiaInformeSemanal() : null;
        return new RespuestaInfoAsignatura(asignaturaId, titulo, prompt, colorTema, temas, sensibilidad,
                emailProfesor, diaInformeSemanal);
    }

    
    @PostMapping("/asignatura/configurar")
    public ResponseEntity<RespuestaConfiguracion> configurarAsignatura(
            @RequestParam(value = "titulo", required = false) String titulo,
            @RequestParam("systemPrompt") String systemPrompt,
            @RequestParam(value = "colorTema", required = false) String colorTema,
            @RequestParam(value = "sensibilidad", required = false) Integer sensibilidad,
            @RequestParam(value = "emailProfesor", required = false) String emailProfesor,
            @RequestParam(value = "diaInformeSemanal", required = false) Integer diaInformeSemanal,
            @RequestParam(value = "archivos", required = false) MultipartFile[] archivos,
            HttpSession session) {

        String asignaturaId = asignaturaDe(session);

        int documentos = servicioIngesta.configurarAsignatura(asignaturaId, titulo, systemPrompt, colorTema,
                sensibilidad, emailProfesor, diaInformeSemanal, archivos);

        return ResponseEntity.ok(new RespuestaConfiguracion(
                true,
                documentos,
                titulo != null ? titulo : "Tutor Socrático",
                colorTema != null ? colorTema : "#58a6ff",
                "Asignatura configurada correctamente. Archivos nuevos procesados: " + documentos));
    }

    @PostMapping("/asignatura/borrar-archivo")
    public ResponseEntity<RespuestaExito> borrarArchivo(
            @RequestParam("nombreArchivo") String nombreArchivo,
            HttpSession session) {

        String asignaturaId = asignaturaDe(session);

        boolean ok = servicioIngesta.borrarArchivo(asignaturaId, nombreArchivo);
        return ResponseEntity.ok(new RespuestaExito(ok));
    }

    @PostMapping("/asignatura/informe-semanal/probar")
    public RespuestaExito probarInformeSemanal(HttpSession session) {
        String asig = asignaturaDe(session);
        boolean ok = servicioInformeSemanal.enviarInforme(asig, true);
        return new RespuestaExito(ok);
    }

    @GetMapping("/asignatura/radar-confusion")
    public RespuestaRadar radarConfusion(@RequestParam(defaultValue = "2") int semanas,
                                         HttpSession session) {
        String asig = asignaturaDe(session);
        int sem = Math.max(1, Math.min(6, semanas));
        LocalDateTime desde = LocalDateTime.now().minusWeeks(sem);
        List<RegistroConsulta> ultimas = repositorio.buscarChatDesde(asig, desde, PageRequest.of(0, 200));
        List<String> preguntas = ultimas.stream().map(RegistroConsulta::getPregunta).toList();
        String analisis = servicioTutor.analizarPuntosCiegos(preguntas, asig);
        return new RespuestaRadar(asig, analisis != null ? analisis : "");
    }

    

    @PostMapping("/estancamiento/sensibilidad")
    public RespuestaSensibilidad guardarSensibilidad(@RequestParam int valor,
                                                     HttpSession session) {
        String asig = asignaturaDe(session);
        int aux = servicioEstancamiento.guardarSensibilidad(asig, valor);
        return new RespuestaSensibilidad(true, aux);
    }

    @GetMapping("/estancamiento/alumnos")
    public List<Map<String, Object>> alumnosConProblemas(HttpSession session) {
        String asig = asignaturaDe(session);
        return servicioEstancamiento.listarAlumnosConProblemas(asig);
    }

    @PostMapping("/estancamiento/resolver")
    public RespuestaExito resolverAviso(@RequestParam Long avisoId) {
        boolean ok = servicioEstancamiento.resolverAviso(avisoId);
        return new RespuestaExito(ok);
    }

    @GetMapping("/consultas")
    public List<ConsultaDTO> consultas(
            @RequestParam(required = false) String alumno,
            @RequestParam(required = false) String tema,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "200") int limite,
            HttpSession session) {

        String asig = asignaturaDe(session);
        List<RegistroConsulta> registros = repositorio.buscarConFiltros(asig,
                normalizar(alumno), normalizar(tema), inicioDe(desde), finDe(hasta),
                PageRequest.of(0, limite));

        return registros.stream().map(ConsultaDTO::desde).toList();
    }

    @GetMapping("/resumen")
    public RespuestaResumen resumen(HttpSession session) {
        String asig = asignaturaDe(session);
        return new RespuestaResumen(
                repositorio.countByAsignaturaId(asig),
                repositorio.contarAlumnosActivos(asig),
                repositorio.contarPorTema(asig),
                repositorio.contarPorAlumno(asig));
    }

    @GetMapping("/perfiles")
    public List<PerfilDocente> perfiles(HttpSession session) {
        String asig = asignaturaDe(session);
        return perfilRepositorio.findByAsignaturaId(asig).stream()
                .map(p -> {
                    int teorico = Math.max(1, p.getContadorTeorico());
                    int practico = Math.max(1, p.getContadorPractico());
                    int porcentajeTeorico = Math.round(100f * teorico / (teorico + practico));
                    int totalUtilidad = p.getContadorUtil() + p.getContadorNoUtil();
                    int porcentajeUtil = totalUtilidad == 0 ? 100 : Math.round(100f * p.getContadorUtil() / totalUtilidad);
                    return new PerfilDocente(p.getUsername(), porcentajeTeorico,
                            100 - porcentajeTeorico, teorico + practico - 2, porcentajeUtil, rachaVigente(p));
                })
                .sorted((a, b) -> a.alumno().compareTo(b.alumno()))
                .toList();
    }

    private int rachaVigente(PerfilAlumnoRegistro p) {
        LocalDate ultimo = p.getUltimoDiaActivo();
        if (ultimo == null) return 0;
        long dias = java.time.temporal.ChronoUnit.DAYS.between(ultimo, LocalDate.now());
        return dias <= 1 ? p.getRachaActual() : 0;
    }

    private static final int TAM_PAGINA_INFORME = 500;

    @GetMapping("/informe")
    public ResponseEntity<StreamingResponseBody> informe(
            @RequestParam(required = false) String alumno,
            @RequestParam(required = false) String tema,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            HttpSession session) {

        String asig = asignaturaDe(session);
        String alumnoFiltro = normalizar(alumno);
        String temaFiltro = normalizar(tema);
        LocalDateTime desdeFiltro = inicioDe(desde);
        LocalDateTime hastaFiltro = finDe(hasta);

        StreamingResponseBody cuerpo = out -> {
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            Writer writer = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
            writer.write('﻿'); 
            writer.write("id,fechaHora,alumno,tema,tipo,pregunta,respuesta,fase,iteracion,valoracion,comentarioValoracion\n");

            int pagina = 0;
            List<RegistroConsulta> lote;
            do {
                lote = repositorio.buscarConFiltros(asig, alumnoFiltro, temaFiltro, desdeFiltro, hastaFiltro,
                        PageRequest.of(pagina, TAM_PAGINA_INFORME));
                for (RegistroConsulta r : lote) {
                    writer.write(filaCsv(r, fmt));
                }
                writer.flush();
                pagina++;
            } while (lote.size() == TAM_PAGINA_INFORME);
            writer.flush();
        };

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"informe-consultas.csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(cuerpo);
    }

    private String filaCsv(RegistroConsulta r, DateTimeFormatter fmt) {
        return String.valueOf(r.getId()) + ',' +
                escaparCsv(r.getFechaHora().format(fmt)) + ',' +
                escaparCsv(r.getUsername()) + ',' +
                escaparCsv(r.getTema()) + ',' +
                escaparCsv(r.getTipo().name()) + ',' +
                escaparCsv(r.getPregunta()) + ',' +
                escaparCsv(r.getRespuesta()) + ',' +
                escaparCsv(r.getFase()) + ',' +
                (r.getIteracion() == null ? "" : r.getIteracion()) + ',' +
                (r.getValoracion() == null ? "" : r.getValoracion()) + ',' +
                escaparCsv(r.getComentarioValoracion()) +
                '\n';
    }

    static String escaparCsv(String valor) {
        if (valor == null) {return "";}
        String v = valor;
        char primero = v.isEmpty() ? '\0' : v.charAt(0);
        if (primero == '=' || primero == '+' || primero == '-' || primero == '@' || primero == '\t' || primero == '\r') {
            v = "'" + v;
        }
        if (v.contains("\"") || v.contains(",") || v.contains("\n") || v.contains("\r")) {return "\"" + v.replace("\"", "\"\"") + "\"";}
        return v;
    }

    private String normalizar(String valor) {
        return (valor == null || valor.isBlank()) ? null : valor;
    }

    private LocalDateTime inicioDe(LocalDate fecha) {
        return fecha == null ? null : fecha.atStartOfDay();
    }

    private LocalDateTime finDe(LocalDate fecha) {
        return fecha == null ? null : fecha.atTime(LocalTime.MAX);
    }
}
