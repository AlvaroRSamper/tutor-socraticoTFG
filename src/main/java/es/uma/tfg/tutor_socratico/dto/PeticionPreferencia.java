package es.uma.tfg.tutor_socratico.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PeticionPreferencia(
    @NotBlank(message = "La opción no puede estar vacía")
    @Pattern(regexp = "A|B", message = "La opción debe ser 'A' o 'B'")
    String opcion
) {}
