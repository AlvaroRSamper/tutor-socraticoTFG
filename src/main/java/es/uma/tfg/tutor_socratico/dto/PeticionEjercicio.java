package es.uma.tfg.tutor_socratico.dto;

import jakarta.validation.constraints.NotBlank;

public record PeticionEjercicio(
    @NotBlank(message = "El tema no puede estar vacío") 
    String tema, 
    
    @NotBlank(message = "La dificultad no puede estar vacía") 
    String dificultad
) {}
