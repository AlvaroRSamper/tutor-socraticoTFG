package es.uma.tfg.tutor_socratico.dto;

import java.util.List;

public record RespuestaEstadoReto(
    Long resolucionId,
    Long ejercicioId,
    String titulo,
    String enunciado,
    String lenguaje,
    Integer porcentajeIndependencia,
    Integer porcentajeAutonomia,
    Integer porcentajeAutoria,
    boolean completado,
    String comentarioDocente,
    List<MicrohitoDTO> microhitos
) {}
