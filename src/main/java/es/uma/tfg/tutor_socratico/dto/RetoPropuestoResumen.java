package es.uma.tfg.tutor_socratico.dto;

public record RetoPropuestoResumen(
    Long ejercicioId,
    String titulo,
    String dificultad,
    String tema,
    int nMicrohitos,
    boolean completadoPorMi,
    String estadoMio,
    int hitosCompletados
) {}
