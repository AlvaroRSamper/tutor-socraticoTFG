package es.uma.tfg.tutor_socratico.dto;

public record TendenciaTema(
        String tema,
        int direccion,
        double mediaReciente,
        double mediaPrevia,
        int muestras
) {
}
