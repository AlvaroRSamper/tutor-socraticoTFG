package es.uma.tfg.tutor_socratico.dto;

public record MicrohitoDTO(
        Long id,
        int orden,
        String titulo,
        String descripcion,
        String criterioValidacion,
        String estado,
        Integer independencia
) {}
