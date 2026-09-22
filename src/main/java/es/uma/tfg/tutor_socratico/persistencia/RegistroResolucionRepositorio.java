package es.uma.tfg.tutor_socratico.persistencia;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RegistroResolucionRepositorio extends JpaRepository<RegistroResolucion, Long> {

    List<RegistroResolucion> findByUsernameAndAsignaturaId(String username, String asignaturaId);

    Optional<RegistroResolucion> findFirstByUsernameAndEjercicioIdOrderByFechaInicioDescIdDesc(String username, Long ejercicioId);

    boolean existsByUsernameAndEjercicioIdAndEstado(String username, Long ejercicioId, RegistroResolucion.Estado estado);

    List<RegistroResolucion> findByAsignaturaId(String asignaturaId);
}
