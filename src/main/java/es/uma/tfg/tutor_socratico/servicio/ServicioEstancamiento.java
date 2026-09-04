package es.uma.tfg.tutor_socratico.servicio;

import es.uma.tfg.tutor_socratico.persistencia.Asignatura;
import es.uma.tfg.tutor_socratico.persistencia.AsignaturaRepositorio;
import es.uma.tfg.tutor_socratico.persistencia.AvisoEstancamiento;
import es.uma.tfg.tutor_socratico.persistencia.AvisoEstancamiento.Ambito;
import es.uma.tfg.tutor_socratico.persistencia.AvisoEstancamiento.Estado;
import es.uma.tfg.tutor_socratico.persistencia.AvisoEstancamientoRepositorio;
import es.uma.tfg.tutor_socratico.persistencia.RegistroConsulta;
import es.uma.tfg.tutor_socratico.persistencia.RegistroConsultaRepositorio;
import es.uma.tfg.tutor_socratico.persistencia.RegistroConsultaRepositorio.ActividadAlumno;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class ServicioEstancamiento {

    private static final int SENSIBILIDAD_MIN = 4;
    private static final int SENSIBILIDAD_MAX = 8;
    private static final int SENSIBILIDAD_DEFECTO = 5;

    private static final int DIAS_HISTORIAL = 14;
    private static final int DIAS_INACTIVIDAD = 3;

    private final AsignaturaRepositorio asignaturaRepositorio;
    private final AvisoEstancamientoRepositorio avisoRepositorio;
    private final RegistroConsultaRepositorio consultaRepositorio;

    public ServicioEstancamiento(AsignaturaRepositorio asignaturaRepositorio,
                                 AvisoEstancamientoRepositorio avisoRepositorio,
                                 RegistroConsultaRepositorio consultaRepositorio) {
        this.asignaturaRepositorio = asignaturaRepositorio;
        this.avisoRepositorio = avisoRepositorio;
        this.consultaRepositorio = consultaRepositorio;
    }

    @Transactional
    public void detectarInactivos(String asignaturaId) {
        String asig = normalizar(asignaturaId);
        int umbral = obtenerSensibilidad(asig);
        LocalDateTime ahora = LocalDateTime.now();
        LocalDateTime inicioReciente = ahora.minusDays(DIAS_INACTIVIDAD);
        LocalDateTime inicioPrevia = ahora.minusDays(DIAS_HISTORIAL);

        for (ActividadAlumno a : consultaRepositorio.resumenActividad(asig, inicioPrevia, inicioReciente)) {
            boolean eraActivo = a.getPrevias() >= umbral;
            boolean haParado = a.getRecientes() == 0;
            if (eraActivo && haParado) {
                registrarOActualizarAviso(asig, a.getUsername(), (int) a.getPrevias());
            } else if (a.getRecientes() > 0) {
                cerrarAvisoSiActivo(asig, a.getUsername());
            }
        }
    }

    @Transactional
    public List<Map<String, Object>> listarAlumnosConProblemas(String asignaturaId) {
        String asig = normalizar(asignaturaId);
        detectarInactivos(asig);
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

    private void registrarOActualizarAviso(String asignaturaId, String username, int consultasPrevias) {
        RegistroConsulta ultima = consultaRepositorio
                .buscarConFiltros(asignaturaId, username, null, null, null, PageRequest.of(0, 1))
                .stream().findFirst().orElse(null);
        String tema = (ultima != null && ultima.getTema() != null && !ultima.getTema().isBlank())
                ? ultima.getTema() : "General";
        String pregunta = ultima != null && ultima.getPregunta() != null ? ultima.getPregunta() : "";
        LocalDateTime fechaUltima = ultima != null ? ultima.getFechaHora() : LocalDateTime.now();

        AvisoEstancamiento aviso = avisoRepositorio
                .findFirstByUsernameAndAsignaturaIdAndEstado(username, asignaturaId, Estado.ABIERTO)
                .orElseGet(() -> AvisoEstancamiento.builder()
                        .username(username)
                        .asignaturaId(asignaturaId)
                        .ambito(Ambito.CHAT)
                        .estado(Estado.ABIERTO)
                        .fechaCreacion(LocalDateTime.now())
                        .build());
        aviso.setTema(tema);
        aviso.setPreguntaEjemplo(pregunta);
        aviso.setIteraciones(consultasPrevias);
        aviso.setFechaActualizacion(fechaUltima);
        avisoRepositorio.save(aviso);
    }

    private void cerrarAvisoSiActivo(String asignaturaId, String username) {
        avisoRepositorio.findFirstByUsernameAndAsignaturaIdAndEstado(username, asignaturaId, Estado.ABIERTO)
                .ifPresent(a -> {
                    a.setEstado(Estado.RESUELTO);
                    a.setFechaResuelto(LocalDateTime.now());
                    avisoRepositorio.save(a);
                });
    }

    private int acotarSensibilidad(Integer valor) {
        if (valor == null) return SENSIBILIDAD_DEFECTO;
        return Math.max(SENSIBILIDAD_MIN, Math.min(SENSIBILIDAD_MAX, valor));
    }

    private String normalizar(String asignaturaId) {
        return (asignaturaId == null || asignaturaId.isBlank()) ? "General" : asignaturaId;
    }
}
