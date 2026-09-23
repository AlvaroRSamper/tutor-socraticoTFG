package es.uma.tfg.tutor_socratico.controlador;

import es.uma.tfg.tutor_socratico.dto.AlumnoRadarDTO;
import es.uma.tfg.tutor_socratico.dto.DetalleEjercicioDTO;
import es.uma.tfg.tutor_socratico.dto.EjercicioRadarDTO;
import es.uma.tfg.tutor_socratico.dto.PeticionChatReto;
import es.uma.tfg.tutor_socratico.dto.PeticionCrearEjercicioIa;
import es.uma.tfg.tutor_socratico.dto.PeticionGenerarMicrohitos;
import es.uma.tfg.tutor_socratico.dto.PeticionGenerarTest;
import es.uma.tfg.tutor_socratico.dto.PreguntaTestDTO;
import es.uma.tfg.tutor_socratico.dto.PeticionIniciarReto;
import es.uma.tfg.tutor_socratico.dto.MicrohitoDTO;
import es.uma.tfg.tutor_socratico.dto.PeticionPublicarEjercicio;
import es.uma.tfg.tutor_socratico.dto.PeticionRecargar;
import es.uma.tfg.tutor_socratico.dto.PeticionSubirEjercicio;
import es.uma.tfg.tutor_socratico.dto.PeticionTiempoReto;
import es.uma.tfg.tutor_socratico.dto.RespuestaChatReto;
import es.uma.tfg.tutor_socratico.dto.RespuestaEstadoReto;
import es.uma.tfg.tutor_socratico.dto.RespuestaOperacion;
import es.uma.tfg.tutor_socratico.dto.RespuestaPublicacion;
import es.uma.tfg.tutor_socratico.dto.RetoPropuestoResumen;
import es.uma.tfg.tutor_socratico.excepcion.FuncionDeshabilitadaException;
import es.uma.tfg.tutor_socratico.servicio.ServicioModoReto;
import es.uma.tfg.tutor_socratico.servicio.ServicioReto;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;


@RestController
@RequestMapping("/api/reto")
public class ControladorReto {

    private final ServicioReto servicioReto;
    private final ServicioModoReto servicioModoReto;

    public ControladorReto(ServicioReto servicioReto, ServicioModoReto servicioModoReto) {
        this.servicioReto = servicioReto;
        this.servicioModoReto = servicioModoReto;
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
        String asig = asignaturaDe(session);
        servicioModoReto.exigirDesactivado(asig);
        return servicioReto.crearEjercicioIa(peticion, auth.getName(), asig);
    }

    @PostMapping("/test")
    public List<PreguntaTestDTO> generarTest(@Valid @RequestBody PeticionGenerarTest peticion,
                                             Authentication auth, HttpSession session) {
        String asig = asignaturaDe(session);
        servicioModoReto.exigirDesactivado(asig);
        return servicioReto.generarTest(peticion, auth.getName(), asig);
    }

    @PostMapping("/subir")
    public DetalleEjercicioDTO subir(@Valid @RequestBody PeticionSubirEjercicio peticion,
                                     Authentication auth, HttpSession session) {
        String asig = asignaturaDe(session);
        servicioModoReto.exigirDesactivado(asig);
        return servicioReto.subirEjercicio(peticion, auth.getName(), asig);
    }

    @PostMapping("/subir-archivo")
    public DetalleEjercicioDTO subirArchivo(@RequestParam("archivo") MultipartFile archivo,
                                            @RequestParam(value = "titulo", required = false) String titulo,
                                            @RequestParam(value = "tema", required = false) String tema,
                                            @RequestParam(value = "lenguaje", required = false) String lenguaje,
                                            Authentication auth, HttpSession session) {
        String asig = asignaturaDe(session);
        servicioModoReto.exigirDesactivado(asig);
        String tit = (titulo != null && !titulo.isBlank()) ? titulo
                : (archivo != null && archivo.getOriginalFilename() != null
                    ? archivo.getOriginalFilename().replaceAll("\\.[^.]+$", "") : "Ejercicio subido");
        return servicioReto.subirEjercicioDesdeArchivo(tit, tema, lenguaje, archivo, auth.getName(), asig);
    }

    @PostMapping("/iniciar")
    public RespuestaEstadoReto iniciar(@Valid @RequestBody PeticionIniciarReto peticion,
                                       Authentication auth, HttpSession session) {
        String asig = asignaturaDe(session);
        if (servicioModoReto.activo(asig) && !servicioReto.esPropuestoPublicado(peticion.ejercicioId(), asig)) {
            throw new FuncionDeshabilitadaException("Tu profesor/a ha activado el modo reto: "
                    + "solo puedes trabajar en los retos propuestos en clase.");
        }
        return servicioReto.iniciarResolucion(peticion.ejercicioId(), auth.getName(), asig,
                Boolean.TRUE.equals(peticion.reiniciar()));
    }

    @PostMapping("/tiempo")
    public ResponseEntity<Void> tiempo(@Valid @RequestBody PeticionTiempoReto peticion, Authentication auth) {
        servicioReto.sumarTiempo(peticion.resolucionId(), peticion.segundos(), auth.getName());
        return ResponseEntity.noContent().build();
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

    

    @PostMapping("/profesor/extraer-texto")
    public Map<String, String> extraerTexto(@RequestParam("archivo") MultipartFile archivo) {
        return Map.of("texto", servicioReto.extraerTextoDeArchivo(archivo));
    }

    @PostMapping("/profesor/microhitos")
    public List<MicrohitoDTO> generarMicrohitos(@Valid @RequestBody PeticionGenerarMicrohitos peticion,
                                                Authentication auth, HttpSession session) {
        return servicioReto.proponerMicrohitos(peticion.enunciado(), peticion.lenguaje(),
                asignaturaDe(session), peticion.tema(), auth.getName());
    }

    @PostMapping("/profesor/publicar")
    public RespuestaPublicacion publicar(@Valid @RequestBody PeticionPublicarEjercicio peticion,
                                        Authentication auth, HttpSession session) {
        return servicioReto.publicarEjercicioPropuesto(peticion, auth.getName(), asignaturaDe(session));
    }

    @DeleteMapping("/profesor/ejercicio/{ejercicioId}")
    public RespuestaOperacion borrarPropuesto(@PathVariable Long ejercicioId, HttpSession session) {
        return servicioReto.borrarEjercicioPropuesto(ejercicioId, asignaturaDe(session));
    }

    @GetMapping("/profesor/radar")
    public List<EjercicioRadarDTO> radar(HttpSession session) {
        return servicioReto.radarDocente(asignaturaDe(session));
    }
}
