package es.uma.tfg.tutor_socratico.servicio;

import es.uma.tfg.tutor_socratico.persistencia.Asignatura;
import es.uma.tfg.tutor_socratico.persistencia.AsignaturaRepositorio;
import es.uma.tfg.tutor_socratico.persistencia.RegistroConsulta;
import es.uma.tfg.tutor_socratico.persistencia.RegistroConsultaRepositorio;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
public class ServicioInformeSemanal {

    private static final int DIAS_VENTANA = 7;
    private static final int MAX_PREGUNTAS = 300;
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final AsignaturaRepositorio asignaturaRepositorio;
    private final RegistroConsultaRepositorio consultaRepositorio;
    private final ServicioTutor servicioTutor;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    private final boolean habilitado;
    private final String remitente;

    public ServicioInformeSemanal(AsignaturaRepositorio asignaturaRepositorio,
                                  RegistroConsultaRepositorio consultaRepositorio,
                                  ServicioTutor servicioTutor,
                                  ObjectProvider<JavaMailSender> mailSenderProvider,
                                  @Value("${informe.semanal.enabled:true}") boolean habilitado,
                                  @Value("${informe.semanal.remitente:}") String remitente) {
        this.asignaturaRepositorio = asignaturaRepositorio;
        this.consultaRepositorio = consultaRepositorio;
        this.servicioTutor = servicioTutor;
        this.mailSenderProvider = mailSenderProvider;
        this.habilitado = habilitado;
        this.remitente = remitente;
    }

    @Scheduled(cron = "${informe.semanal.cron:0 0 8 * * *}")
    public void enviarInformesDeHoy() {
        if (!habilitado) return;
        int hoy = LocalDate.now().getDayOfWeek().getValue();
        for (Asignatura asig : asignaturaRepositorio.findAll()) {
            Integer dia = asig.getDiaInformeSemanal();
            if (dia == null || dia != hoy) continue;
            if (asig.getEmailProfesor() == null || asig.getEmailProfesor().isBlank()) continue;
            enviarInforme(asig.getAsignaturaId(), false);
        }
    }

    public boolean enviarInforme(String asignaturaId, boolean incluirSiVacio) {
        Asignatura asig = asignaturaRepositorio.findById(asignaturaId).orElse(null);
        if (asig == null || asig.getEmailProfesor() == null || asig.getEmailProfesor().isBlank()) {
            log.warn("No se envía informe de {}: sin email configurado", asignaturaId);
            return false;
        }

        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            log.warn("No se envía informe de {}: correo no configurado (spring.mail.host)", asignaturaId);
            return false;
        }

        LocalDateTime desde = LocalDateTime.now().minusDays(DIAS_VENTANA);
        List<RegistroConsulta> consultas = consultaRepositorio.buscarConFiltros(
                asignaturaId, null, null, desde, null, PageRequest.of(0, MAX_PREGUNTAS));

        if (consultas.isEmpty() && !incluirSiVacio) {
            log.info("No se envía informe de {}: sin consultas esta semana", asignaturaId);
            return false;
        }

        List<String> preguntas = consultas.stream().map(RegistroConsulta::getPregunta).toList();
        String analisis = servicioTutor.analizarPuntosCiegos(preguntas, asignaturaId);
        String cuerpo = construirCuerpo(analisis, consultas);
        String titulo = asig.getTitulo() != null ? asig.getTitulo() : asignaturaId;

        try {
            SimpleMailMessage mensaje = new SimpleMailMessage();
            if (remitente != null && !remitente.isBlank()) mensaje.setFrom(remitente);
            mensaje.setTo(asig.getEmailProfesor());
            mensaje.setSubject("📊 Informe semanal · " + titulo + " · " + LocalDate.now());
            mensaje.setText(cuerpo);
            mailSender.send(mensaje);
            log.info("Informe semanal de {} enviado a {}", asignaturaId, asig.getEmailProfesor());
            return true;
        } catch (Exception e) {
            log.error("No se pudo enviar el informe semanal de {}: {}", asignaturaId, e.getMessage());
            return false;
        }
    }

    private String construirCuerpo(String analisis, List<RegistroConsulta> consultas) {
        StringBuilder sb = new StringBuilder();
        sb.append(analisis != null ? analisis : "").append("\n\n");
        sb.append("====================================================\n");
        sb.append("PREGUNTAS DE LA SEMANA (").append(consultas.size()).append(")\n");
        sb.append("====================================================\n");
        int i = 1;
        for (RegistroConsulta c : consultas) {
            sb.append(i++).append(". [").append(c.getFechaHora() != null ? c.getFechaHora().format(FMT) : "—").append("] ")
              .append(c.getUsername() != null ? c.getUsername() : "?")
              .append(" · ").append(c.getTema() != null ? c.getTema() : "General")
              .append("\n   ").append(c.getPregunta() != null ? c.getPregunta() : "").append("\n");
        }
        return sb.toString();
    }
}
