package es.uma.tfg.tutor_socratico.persistencia;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApunteAlumnoRepositorio extends JpaRepository<ApunteAlumno, Long> {

    List<ApunteAlumno> findByUsernameAndAsignaturaIdOrderByFechaSubidaDesc(String username, String asignaturaId);
}
