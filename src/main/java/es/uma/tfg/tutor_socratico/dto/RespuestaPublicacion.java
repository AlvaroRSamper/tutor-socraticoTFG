package es.uma.tfg.tutor_socratico.dto;

public record RespuestaPublicacion(
    boolean exito,
    Long ejercicioId,
    String mensaje
) {}
