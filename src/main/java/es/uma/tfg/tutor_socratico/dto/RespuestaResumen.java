package es.uma.tfg.tutor_socratico.dto;

import es.uma.tfg.tutor_socratico.persistencia.RegistroConsultaRepositorio.ConteoPorClave;

import java.util.List;

/**
 * Resumen de métricas de la asignatura para el panel del profesor. Claves JSON:
 * {@code totalConsultas}, {@code alumnosActivos}, {@code porTema}, {@code porAlumno}
 * (cada conteo expone {@code clave} y {@code total}).
 */
public record RespuestaResumen(long totalConsultas, long alumnosActivos,
                               List<ConteoPorClave> porTema, List<ConteoPorClave> porAlumno) {}
