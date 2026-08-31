package es.uma.tfg.tutor_socratico.dto;

public record RespuestaChatReto(
    String mensaje,
    boolean estancamiento,
    Long avisoId,
    String mensajeEstancamiento
) {}
