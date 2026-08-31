package es.uma.tfg.tutor_socratico.persistencia;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PerfilAlumnoRepositorio extends JpaRepository<PerfilAlumnoRegistro, Long> {
    Optional<PerfilAlumnoRegistro> findByUsernameAndAsignaturaId(String username, String asignaturaId);
    List<PerfilAlumnoRegistro> findByAsignaturaId(String asignaturaId);
}
