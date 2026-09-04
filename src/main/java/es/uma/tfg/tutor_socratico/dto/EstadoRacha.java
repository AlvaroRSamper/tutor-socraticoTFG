package es.uma.tfg.tutor_socratico.dto;

public record EstadoRacha(
        boolean aumentada,
        int rachaActual,
        int rachaMaxima,
        int diasAusente,
        boolean mostrarMensajeBienvenida
) {}
