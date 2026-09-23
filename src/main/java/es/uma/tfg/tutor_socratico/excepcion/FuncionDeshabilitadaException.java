package es.uma.tfg.tutor_socratico.excepcion;

public class FuncionDeshabilitadaException extends RuntimeException {
    public FuncionDeshabilitadaException(String mensaje) {
        super(mensaje);
    }
}
