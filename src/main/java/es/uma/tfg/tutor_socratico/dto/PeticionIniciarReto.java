package es.uma.tfg.tutor_socratico.dto;

import jakarta.validation.constraints.NotNull;

public record PeticionIniciarReto(
        @NotNull(message = "El ejercicio es obligatorio")
        Long ejercicioId,
        Boolean reiniciar
) {}
