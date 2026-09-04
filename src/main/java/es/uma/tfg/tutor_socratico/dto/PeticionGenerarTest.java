package es.uma.tfg.tutor_socratico.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record PeticionGenerarTest(
        @NotBlank(message = "El tema no puede estar vacío")
        String tema,

        @NotBlank(message = "La dificultad no puede estar vacía")
        String dificultad,

        @Min(value = 1, message = "El test debe tener al menos una pregunta")
        @Max(value = 30, message = "El test no puede tener más de 30 preguntas")
        int numPreguntas
) {}
