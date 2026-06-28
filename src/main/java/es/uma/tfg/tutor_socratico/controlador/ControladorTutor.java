package es.uma.tfg.tutor_socratico.controlador;

import es.uma.tfg.tutor_socratico.dto.PeticionChat;
import es.uma.tfg.tutor_socratico.dto.PeticionEjercicio;
import es.uma.tfg.tutor_socratico.servicio.ServicioTutor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;

import java.util.Map;

@RestController
@RequestMapping("/api/tutor")
public class ControladorTutor {
    private final ServicioTutor servicioTutor;

    public ControladorTutor(ServicioTutor servicioTutor) {
        this.servicioTutor = servicioTutor;
    }

    @PostMapping("/chat")
    public Map<String, String> chatearConTutor(@Valid @RequestBody PeticionChat peticion) {
        return servicioTutor.consultarTutor(peticion);
    }

    @PostMapping("/ejercicio")
    public Map<String, String> generarEjercicio(@Valid @RequestBody PeticionEjercicio peticion) {
        return servicioTutor.generarEjercicio(peticion);
    }
}
