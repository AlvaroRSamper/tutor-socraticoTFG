package es.uma.tfg.tutor_socratico.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record PeticionRevelacion(
        @NotNull Long consultaId,
        @NotNull @Min(0) @Max(2) Integer nivel
) {
}
