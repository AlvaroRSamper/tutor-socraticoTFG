package es.uma.tfg.tutor_socratico.configuracion;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "tutor.security")
public record PropiedadesUsuarios(List<Usuario> usuarios, String fichero, String asignaturaPorDefecto) {

    public PropiedadesUsuarios {
        if (usuarios == null) {
            usuarios = List.of();
        }
        if (asignaturaPorDefecto == null || asignaturaPorDefecto.isBlank()) {
            asignaturaPorDefecto = "General";
        }
    }

    public record Usuario(String id, String passwordHash, String rol, String asignatura) {
        public Usuario {
            if (rol == null || rol.isBlank()) {
                rol = "ALUMNO";
            }
        }
    }
}
