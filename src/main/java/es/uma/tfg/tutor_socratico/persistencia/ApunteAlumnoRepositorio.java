package es.uma.tfg.tutor_socratico.persistencia;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Transactional(readOnly = true)
public interface ApunteAlumnoRepositorio extends JpaRepository<ApunteAlumno, Long> {

    List<ApunteAlumno> findByUsernameAndAsignaturaIdOrderByFechaSubidaDesc(String username, String asignaturaId);
}
