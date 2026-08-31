package es.uma.tfg.tutor_socratico.dto;

import java.util.List;

public record DetalleEjercicioDTO(
    Long ejercicioId,
    String titulo,
    String enunciado,
    String lenguaje,
    List<MicrohitoDTO> microhitos
) {}
