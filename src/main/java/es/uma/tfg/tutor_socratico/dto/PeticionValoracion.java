package es.uma.tfg.tutor_socratico.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;


public record PeticionValoracion(
        @NotNull(message = "El identificador de la consulta es obligatorio")
        Long consultaId,

        @Min(value = -1, message = "La valoración debe ser -1, 0 o 1")
        @Max(value = 1, message = "La valoración debe ser -1, 0 o 1")
        Integer valoracion,

        String comentario
) {}
