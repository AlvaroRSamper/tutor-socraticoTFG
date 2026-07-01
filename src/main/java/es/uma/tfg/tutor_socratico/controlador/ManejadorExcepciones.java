package es.uma.tfg.tutor_socratico.controlador;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@Slf4j
@RestControllerAdvice
public class ManejadorExcepciones {

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, String> manejarExcepcionGeneral(Exception e) {
        log.error("Error inesperado en el servidor: ", e);
        return Map.of("mensaje", "Ha ocurrido un error inesperado al procesar la solicitud. Por favor, inténtelo de nuevo.");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> manejarExcepcionValidacion(MethodArgumentNotValidException e) {
        log.error("Error de validación de entrada: {}", e.getMessage());
        return Map.of("mensaje", "Datos de entrada no válidos. Verifique la petición.");
    }
}
