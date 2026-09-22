package es.uma.tfg.tutor_socratico.persistencia;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Transactional(readOnly = true)
public interface EjercicioRepositorio extends JpaRepository<Ejercicio, Long> {

    List<Ejercicio> findByAsignaturaIdAndPublicadoTrueOrderByFechaCreacionDesc(String asignaturaId);
}
