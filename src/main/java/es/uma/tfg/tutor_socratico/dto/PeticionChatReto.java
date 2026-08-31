package es.uma.tfg.tutor_socratico.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record PeticionChatReto(
        @NotNull(message = "La resolución es obligatoria")
        Long resolucionId,

        @NotEmpty(message = "El historial no puede estar vacío")
        List<Mensaje> historial
) {}
