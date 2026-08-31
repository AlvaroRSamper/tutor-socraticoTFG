package es.uma.tfg.tutor_socratico.dto;

import com.fasterxml.jackson.annotation.JsonInclude;


@JsonInclude(JsonInclude.Include.NON_NULL)
public record RespuestaChat(
        String mensaje,
        String fase,
        boolean mostrarOpciones,
        long consultaId,
        boolean estancamiento,
        long avisoId,
        String mensajeEstancamiento
) {
    
    public static RespuestaChat de(String mensaje, String fase, boolean mostrarOpciones, long consultaId,
                                   boolean estancamiento, long avisoId, String mensajeEstancamiento) {
        return new RespuestaChat(mensaje, fase, mostrarOpciones, consultaId, estancamiento, avisoId, mensajeEstancamiento);
    }

    
    public static RespuestaChat error(String mensaje) {
        return new RespuestaChat(mensaje, "PERMANENTE", false, 0L, false, 0L, null);
    }
}
