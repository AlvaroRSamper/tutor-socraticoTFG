package es.uma.tfg.tutor_socratico.controlador;

import es.uma.tfg.tutor_socratico.dto.EstadoRacha;
import es.uma.tfg.tutor_socratico.dto.Mensaje;
import es.uma.tfg.tutor_socratico.dto.PeticionChat;
import es.uma.tfg.tutor_socratico.dto.PeticionEjercicio;
import es.uma.tfg.tutor_socratico.dto.PeticionPreferencia;
import es.uma.tfg.tutor_socratico.dto.PeticionValoracion;
import es.uma.tfg.tutor_socratico.dto.RespuestaApuntes;
import es.uma.tfg.tutor_socratico.dto.RespuestaChat;
import es.uma.tfg.tutor_socratico.dto.RespuestaEjercicio;
import es.uma.tfg.tutor_socratico.dto.RespuestaExito;
import es.uma.tfg.tutor_socratico.dto.RespuestaMensaje;
import es.uma.tfg.tutor_socratico.dto.RespuestaTemas;
import es.uma.tfg.tutor_socratico.perfil.PerfilAprendizajeServicio;
import es.uma.tfg.tutor_socratico.persistencia.Asignatura;
import es.uma.tfg.tutor_socratico.persistencia.AsignaturaRepositorio;
import es.uma.tfg.tutor_socratico.servicio.ServicioConsentimiento;
import es.uma.tfg.tutor_socratico.servicio.ServicioRacha;
import es.uma.tfg.tutor_socratico.servicio.ServicioRegistroConsultas;
import es.uma.tfg.tutor_socratico.servicio.ServicioTutor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

import java.util.List;
import java.util.Map;
@RestController
@RequestMapping("/api/tutor")
public class ControladorTutor {
    private final ServicioTutor servicioTutor;
    private final PerfilAprendizajeServicio perfilAprendizajeServicio;
    private final AsignaturaRepositorio asignaturaRepositorio;
    private final ServicioRegistroConsultas servicioRegistroConsultas;
    private final ServicioRacha servicioRacha;
    private final ServicioConsentimiento servicioConsentimiento;

    public ControladorTutor(ServicioTutor servicioTutor, PerfilAprendizajeServicio perfilAprendizajeServicio,
                            AsignaturaRepositorio asignaturaRepositorio, ServicioRegistroConsultas servicioRegistroConsultas,
                            ServicioRacha servicioRacha, ServicioConsentimiento servicioConsentimiento) {
        this.servicioTutor = servicioTutor;
        this.perfilAprendizajeServicio = perfilAprendizajeServicio;
        this.asignaturaRepositorio = asignaturaRepositorio;
        this.servicioRegistroConsultas = servicioRegistroConsultas;
        this.servicioRacha = servicioRacha;
        this.servicioConsentimiento = servicioConsentimiento;
    }

    @GetMapping("/temas")
    public RespuestaTemas obtenerTemas(HttpSession session, Authentication authentication) {
        String asignaturaId = asignaturaDe(session);
        Asignatura asig = asignaturaRepositorio.findById(asignaturaId).orElse(null);
        String temas = (asig != null && asig.getTemas() != null) ? asig.getTemas() : "";
        String titulo = (asig != null && asig.getTitulo() != null) ? asig.getTitulo() : "Tutor Socrático";
        String colorTema = (asig != null && asig.getColorTema() != null) ? asig.getColorTema() : "github-dark";
        String username = (authentication != null) ? authentication.getName() : null;
        return new RespuestaTemas(asignaturaId, temas, titulo, colorTema, username);
    }

    @PostMapping("/racha")
    public EstadoRacha registrarRachaDiaria(Authentication authentication, HttpSession session) {
        return servicioRacha.registrarEntradaDiaria(authentication.getName(), asignaturaDe(session));
    }

    @GetMapping("/consentimiento")
    public Map<String, Boolean> estadoConsentimiento(Authentication authentication, HttpSession session) {
        return Map.of("aceptado", servicioConsentimiento.haAceptado(authentication.getName(), asignaturaDe(session)));
    }

    @PostMapping("/consentimiento")
    public Map<String, Boolean> aceptarConsentimiento(Authentication authentication, HttpSession session) {
        servicioConsentimiento.aceptar(authentication.getName(), asignaturaDe(session));
        return Map.of("aceptado", true);
    }

    @PostMapping("/chat")
    public RespuestaChat chatearConTutor(@Valid @RequestBody PeticionChat peticion,
                                         Authentication authentication,
                                         HttpSession session) {
        String asignaturaId = asignaturaDe(session);
        return servicioTutor.consultarTutor(peticion, authentication.getName(), asignaturaId);
    }

    @PostMapping("/ejercicio")
    public RespuestaEjercicio generarEjercicio(@Valid @RequestBody PeticionEjercicio peticion,
                                               Authentication authentication,
                                               HttpSession session) {
        String asignaturaId = asignaturaDe(session);
        return servicioTutor.generarEjercicio(peticion, authentication.getName(), asignaturaId);
    }

    private String asignaturaDe(HttpSession session) {
        String asig = (String) session.getAttribute("asignatura_id");
        return (asig == null || asig.isBlank()) ? "General" : asig;
    }

    @PostMapping("/preferencia")
    public RespuestaMensaje registrarPreferencia(@Valid @RequestBody PeticionPreferencia peticion,
                                                 Authentication authentication,
                                                 HttpSession session) {
        String asignaturaId = asignaturaDe(session);
        perfilAprendizajeServicio.registrarPreferencia(authentication.getName(), asignaturaId, peticion.opcion());
        return new RespuestaMensaje("Preferencia registrada");
    }

    @PostMapping("/valoracion")
    public RespuestaExito valorarConsulta(@Valid @RequestBody PeticionValoracion peticion, Authentication authentication) {
        boolean ok = servicioRegistroConsultas.registrarValoracion(
                peticion.consultaId(), peticion.valoracion(), peticion.comentario());
        return new RespuestaExito(ok);
    }

    @PostMapping("/repaso")
    public RespuestaApuntes generarRepaso(@RequestBody Map<String, List<Mensaje>> cuerpo, HttpSession session) {
        List<Mensaje> historial = cuerpo.get("historial");
        String asignaturaId = asignaturaDe(session);
        String md = servicioTutor.generarApuntesRepaso(historial, asignaturaId);
        return new RespuestaApuntes(md != null ? md : "");
    }
}
