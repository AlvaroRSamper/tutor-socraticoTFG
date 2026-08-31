package es.uma.tfg.tutor_socratico.dto;

import java.util.List;

public record EjercicioRadarDTO(
    Long ejercicioId,
    String titulo,
    String dificultad,
    int nAlumnos,
    int nCompletados,
    Integer independenciaMedia,
    Integer autonomiaMedia,
    Integer autoriaMedia,
    List<AlumnoRadarDTO> alumnos
) {}
