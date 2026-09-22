package es.uma.tfg.tutor_socratico.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record PeticionTiempoReto(
        @NotNull(message = "La resolución es obligatoria")
        Long resolucionId,
        @NotNull(message = "Los segundos son obligatorios")
        @Min(value = 0, message = "Los segundos no pueden ser negativos")
        Integer segundos
) {}
