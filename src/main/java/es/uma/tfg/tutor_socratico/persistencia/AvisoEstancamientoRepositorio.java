package es.uma.tfg.tutor_socratico.persistencia;

import es.uma.tfg.tutor_socratico.persistencia.AvisoEstancamiento.Ambito;
import es.uma.tfg.tutor_socratico.persistencia.AvisoEstancamiento.Estado;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AvisoEstancamientoRepositorio extends JpaRepository<AvisoEstancamiento, Long> {


    List<AvisoEstancamiento> findByAsignaturaIdAndEstadoOrderByFechaActualizacionDesc(String asignaturaId, Estado estado);

    Optional<AvisoEstancamiento> findFirstByUsernameAndAsignaturaIdAndAmbitoAndTemaAndEstado(
            String username, String asignaturaId, Ambito ambito, String tema, Estado estado);
}
