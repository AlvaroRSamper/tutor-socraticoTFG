package es.uma.tfg.tutor_socratico.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record PeticionRecargar(
        @NotNull(message = "La resolución es obligatoria")
        Long resolucionId,

        List<ArchivoCodigo> archivos
) {}
