package es.uma.tfg.tutor_socratico.persistencia;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RegistroResolucionRepositorio extends JpaRepository<RegistroResolucion, Long> {

    List<RegistroResolucion> findByUsernameAndAsignaturaId(String username, String asignaturaId);

    Optional<RegistroResolucion> findFirstByUsernameAndEjercicioIdOrderByFechaInicioDesc(String username, Long ejercicioId);

    List<RegistroResolucion> findByAsignaturaId(String asignaturaId);
}
