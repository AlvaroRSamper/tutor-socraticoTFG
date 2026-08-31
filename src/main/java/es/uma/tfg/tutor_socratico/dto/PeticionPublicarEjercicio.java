package es.uma.tfg.tutor_socratico.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record PeticionPublicarEjercicio(
        @NotBlank(message = "El título no puede estar vacío")
        String titulo,

        @NotBlank(message = "El enunciado no puede estar vacío")
        String enunciado,

        String dificultad,
        String tema,
        String lenguaje,

        @NotEmpty(message = "Debes desglosar al menos un microhito")
        List<MicrohitoDTO> microhitos
) {}
