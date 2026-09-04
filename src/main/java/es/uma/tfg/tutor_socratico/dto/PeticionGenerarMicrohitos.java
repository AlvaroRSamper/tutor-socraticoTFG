package es.uma.tfg.tutor_socratico.dto;

import jakarta.validation.constraints.NotBlank;

public record PeticionGenerarMicrohitos(
        @NotBlank(message = "El enunciado no puede estar vacío") String enunciado,
        String lenguaje,
        String tema
) {}
