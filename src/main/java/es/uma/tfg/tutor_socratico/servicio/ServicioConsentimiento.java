package es.uma.tfg.tutor_socratico.servicio;

import es.uma.tfg.tutor_socratico.persistencia.PerfilAlumnoRegistro;
import es.uma.tfg.tutor_socratico.persistencia.PerfilAlumnoRepositorio;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class ServicioConsentimiento {

    private final PerfilAlumnoRepositorio repositorio;

    public ServicioConsentimiento(PerfilAlumnoRepositorio repositorio) {
        this.repositorio = repositorio;
    }

    public boolean haAceptado(String username, String asignaturaId) {
        return repositorio.findByUsernameAndAsignaturaId(username, normalizar(asignaturaId))
                .map(PerfilAlumnoRegistro::isConsentimientoDatos)
                .orElse(false);
    }

    @Transactional
    public void aceptar(String username, String asignaturaId) {
        String asig = normalizar(asignaturaId);
        PerfilAlumnoRegistro reg = repositorio.findByUsernameAndAsignaturaId(username, asig)
                .orElseGet(() -> PerfilAlumnoRegistro.builder().username(username).asignaturaId(asig).build());
        if (!reg.isConsentimientoDatos()) {
            reg.setConsentimientoDatos(true);
            reg.setFechaConsentimiento(LocalDateTime.now());
            repositorio.save(reg);
        }
    }

    private String normalizar(String asignaturaId) {
        return (asignaturaId == null || asignaturaId.isBlank()) ? "General" : asignaturaId;
    }
}
