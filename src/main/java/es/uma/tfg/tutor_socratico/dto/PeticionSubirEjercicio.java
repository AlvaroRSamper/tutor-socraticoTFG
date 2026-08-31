package es.uma.tfg.tutor_socratico.dto;

import jakarta.validation.constraints.NotBlank;

public record PeticionSubirEjercicio(
        @NotBlank(message = "El título no puede estar vacío")
        String titulo,

        @NotBlank(message = "El enunciado no puede estar vacío")
        String enunciado,

        String tema,
        String lenguaje
) {}
