package es.uma.tfg.tutor_socratico.controlador;

import es.uma.tfg.tutor_socratico.dto.AlumnoRadarDTO;
import es.uma.tfg.tutor_socratico.dto.DetalleEjercicioDTO;
import es.uma.tfg.tutor_socratico.dto.EjercicioRadarDTO;
import es.uma.tfg.tutor_socratico.dto.PeticionChatReto;
import es.uma.tfg.tutor_socratico.dto.PeticionCrearEjercicioIa;
import es.uma.tfg.tutor_socratico.dto.PeticionIniciarReto;
import es.uma.tfg.tutor_socratico.dto.PeticionPublicarEjercicio;
import es.uma.tfg.tutor_socratico.dto.PeticionRecargar;
import es.uma.tfg.tutor_socratico.dto.PeticionSubirEjercicio;
import es.uma.tfg.tutor_socratico.dto.RespuestaChatReto;
import es.uma.tfg.tutor_socratico.dto.RespuestaEstadoReto;
import es.uma.tfg.tutor_socratico.dto.RespuestaPublicacion;
import es.uma.tfg.tutor_socratico.dto.RetoPropuestoResumen;
import es.uma.tfg.tutor_socratico.servicio.ServicioReto;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;


@RestController
@RequestMapping("/api/reto")
public class ControladorReto {

    private final ServicioReto servicioReto;

    public ControladorReto(ServicioReto servicioReto) {
        this.servicioReto = servicioReto;
    }

    private String asignaturaDe(HttpSession session) {
        String asig = (String) session.getAttribute("asignatura_id");
        return (asig == null || asig.isBlank()) ? "General" : asig;
    }

    

    @GetMapping("/propuestos")
    public List<RetoPropuestoResumen> propuestos(Authentication auth, HttpSession session) {
        return servicioReto.listarPropuestos(auth.getName(), asignaturaDe(session));
    }

    @PostMapping("/crear-ia")
    public DetalleEjercicioDTO crearIa(@Valid @RequestBody PeticionCrearEjercicioIa peticion,
                                       Authentication auth, HttpSession session) {
        return servicioReto.crearEjercicioIa(peticion, auth.getName(), asignaturaDe(session));
    }

    @PostMapping("/subir")
    public DetalleEjercicioDTO subir(@Valid @RequestBody PeticionSubirEjercicio peticion,
                                     Authentication auth, HttpSession session) {
        return servicioReto.subirEjercicio(peticion, auth.getName(), asignaturaDe(session));
    }

    @PostMapping("/iniciar")
    public RespuestaEstadoReto iniciar(@Valid @RequestBody PeticionIniciarReto peticion,
                                       Authentication auth, HttpSession session) {
        return servicioReto.iniciarResolucion(peticion.ejercicioId(), auth.getName(), asignaturaDe(session));
    }

    @PostMapping("/chat")
    public RespuestaChatReto chat(@Valid @RequestBody PeticionChatReto peticion,
                                    Authentication auth, HttpSession session) {
        return servicioReto.chatReto(peticion, auth.getName(), asignaturaDe(session));
    }

    @PostMapping("/recargar")
    public RespuestaEstadoReto recargar(@Valid @RequestBody PeticionRecargar peticion, Authentication auth) {
        return servicioReto.recargar(peticion, auth.getName());
    }

    

    @PostMapping("/profesor/publicar")
    public RespuestaPublicacion publicar(@Valid @RequestBody PeticionPublicarEjercicio peticion,
                                        Authentication auth, HttpSession session) {
        return servicioReto.publicarEjercicioPropuesto(peticion, auth.getName(), asignaturaDe(session));
    }

    @GetMapping("/profesor/radar")
    public List<EjercicioRadarDTO> radar(HttpSession session) {
        return servicioReto.radarDocente(asignaturaDe(session));
    }
}
