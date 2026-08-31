package es.uma.tfg.tutor_socratico.dto;

import es.uma.tfg.tutor_socratico.persistencia.RegistroConsulta;

import java.time.LocalDateTime;


public record ConsultaDTO(
        Long id,
        String username,
        LocalDateTime fechaHora,
        String tema,
        String tipo,
        String pregunta,
        String respuesta,
        String fase,
        Integer iteracion,
        Integer valoracion,
        String comentarioValoracion
) {
    public static ConsultaDTO desde(RegistroConsulta r) {
        return new ConsultaDTO(
                r.getId(),
                r.getUsername(),
                r.getFechaHora(),
                r.getTema(),
                r.getTipo().name(),
                r.getPregunta(),
                r.getRespuesta(),
                r.getFase(),
                r.getIteracion(),
                r.getValoracion(),
                r.getComentarioValoracion());
    }
}
