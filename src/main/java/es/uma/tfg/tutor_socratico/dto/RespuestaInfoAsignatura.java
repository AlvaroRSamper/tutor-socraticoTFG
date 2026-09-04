package es.uma.tfg.tutor_socratico.dto;


public record RespuestaInfoAsignatura(String asignaturaId, String titulo, String systemPrompt,
                                      String colorTema, String temas, int sensibilidad,
                                      String emailProfesor, Integer diaInformeSemanal) {}
