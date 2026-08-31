package es.uma.tfg.tutor_socratico.dto;

public record AlumnoRadarDTO(
    String alumno,
    String estado,
    Integer independencia,
    Integer autonomia,
    Integer autoria,
    Integer tiempoSegundos,
    String fechaFin
) {}
