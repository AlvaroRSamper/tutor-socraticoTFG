package es.uma.tfg.tutor_socratico.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record PeticionChat(
    @NotEmpty(message = "El historial no puede estar vacío") 
    @NotNull(message = "El historial es obligatorio") 
    List<Mensaje> historial, 
    
    @NotBlank(message = "El tema no puede estar vacío") 
    String tema
) {}
