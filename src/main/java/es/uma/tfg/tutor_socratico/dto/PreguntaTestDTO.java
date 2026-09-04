package es.uma.tfg.tutor_socratico.dto;

import java.util.List;

public record PreguntaTestDTO(
        String enunciado,
        List<String> opciones,
        int correcta,
        String explicacion
) {}
