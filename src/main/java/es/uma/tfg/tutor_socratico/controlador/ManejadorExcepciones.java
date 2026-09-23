package es.uma.tfg.tutor_socratico.controlador;

import es.uma.tfg.tutor_socratico.excepcion.FuncionDeshabilitadaException;
import es.uma.tfg.tutor_socratico.excepcion.RecursoNoEncontradoException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.Map;

@Slf4j
@RestControllerAdvice
public class ManejadorExcepciones {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> manejarExcepcionValidacion(MethodArgumentNotValidException e) {
        log.warn("Error de validación de entrada: {}", e.getMessage());
        return Map.of("mensaje", "Datos de entrada no válidos. Verifique la petición.");
    }

    
    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> manejarPeticionMalFormada(Exception e) {
        log.warn("Petición mal formada: {}", e.getMessage());
        return Map.of("mensaje", "Petición mal formada. Verifique los datos enviados.");
    }

    
    @ExceptionHandler({RecursoNoEncontradoException.class, org.springframework.web.servlet.resource.NoResourceFoundException.class})
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> manejarNoEncontrado(Exception e) {
        log.warn("Recurso no encontrado: {}", e.getMessage());
        return Map.of("mensaje", "El recurso solicitado no existe o no está disponible.");
    }

    
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    public Map<String, String> manejarArchivoDemasiadoGrande(MaxUploadSizeExceededException e) {
        log.warn("Archivo demasiado grande: {}", e.getMessage());
        return Map.of("mensaje", "El archivo supera el tamaño máximo permitido.");
    }

    
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public Map<String, String> manejarMetodoNoPermitido(HttpRequestMethodNotSupportedException e) {
        log.warn("Método no permitido: {}", e.getMessage());
        return Map.of("mensaje", "Método no permitido para este recurso.");
    }

    
    @ExceptionHandler(FuncionDeshabilitadaException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Map<String, String> manejarFuncionDeshabilitada(FuncionDeshabilitadaException e) {
        log.warn("Funcionalidad deshabilitada por el profesor: {}", e.getMessage());
        return Map.of("mensaje", e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> manejarArgumentoInvalido(IllegalArgumentException e) {
        log.warn("Petición inválida: {}", e.getMessage());
        return Map.of("mensaje", e.getMessage() != null ? e.getMessage() : "Petición inválida.");
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, String> manejarExcepcionGeneral(Exception e) {
        log.error("Error inesperado en el servidor: ", e);
        return Map.of("mensaje", "Ha ocurrido un error inesperado en el servidor. Inténtalo de nuevo más tarde.");
    }
}
