package es.uma.tfg.tutor_socratico.persistencia;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EjercicioRepositorio extends JpaRepository<Ejercicio, Long> {

    List<Ejercicio> findByAsignaturaIdAndPublicadoTrueOrderByFechaCreacionDesc(String asignaturaId);
}
