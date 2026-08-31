package es.uma.tfg.tutor_socratico.dto;


public record RespuestaConfiguracion(boolean exito, int documentosProcesados, String titulo,
                                     String colorTema, String mensaje) {}
