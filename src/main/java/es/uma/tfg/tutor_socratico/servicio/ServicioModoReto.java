package es.uma.tfg.tutor_socratico.servicio;

import es.uma.tfg.tutor_socratico.excepcion.FuncionDeshabilitadaException;
import es.uma.tfg.tutor_socratico.persistencia.Asignatura;
import es.uma.tfg.tutor_socratico.persistencia.AsignaturaRepositorio;
import org.springframework.stereotype.Service;

@Service
public class ServicioModoReto {

    private static final String MENSAJE = "Tu profesor/a ha activado el modo reto: "
            + "ahora mismo solo están disponibles los retos propuestos en clase.";

    private final AsignaturaRepositorio asignaturaRepositorio;

    public ServicioModoReto(AsignaturaRepositorio asignaturaRepositorio) {
        this.asignaturaRepositorio = asignaturaRepositorio;
    }

    public boolean activo(String asignaturaId) {
        return asignaturaRepositorio.findById(normalizar(asignaturaId))
                .map(Asignatura::getModoRetoExclusivo)
                .orElse(false);
    }

    public void exigirDesactivado(String asignaturaId) {
        if (activo(asignaturaId)) {
            throw new FuncionDeshabilitadaException(MENSAJE);
        }
    }

    public boolean guardar(String asignaturaId, boolean activo) {
        String asig = normalizar(asignaturaId);
        Asignatura a = asignaturaRepositorio.findById(asig)
                .orElseGet(() -> Asignatura.builder().asignaturaId(asig).build());
        a.setModoRetoExclusivo(activo);
        asignaturaRepositorio.save(a);
        return activo;
    }

    private String normalizar(String asignaturaId) {
        return (asignaturaId == null || asignaturaId.isBlank()) ? "General" : asignaturaId;
    }
}
