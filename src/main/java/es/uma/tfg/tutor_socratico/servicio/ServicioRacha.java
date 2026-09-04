package es.uma.tfg.tutor_socratico.servicio;

import es.uma.tfg.tutor_socratico.dto.EstadoRacha;
import es.uma.tfg.tutor_socratico.persistencia.PerfilAlumnoRegistro;
import es.uma.tfg.tutor_socratico.persistencia.PerfilAlumnoRepositorio;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Service
public class ServicioRacha {

    private static final int DIAS_BIENVENIDA = 3;

    private final PerfilAlumnoRepositorio repositorio;

    public ServicioRacha(PerfilAlumnoRepositorio repositorio) {
        this.repositorio = repositorio;
    }

    @Transactional
    public EstadoRacha registrarEntradaDiaria(String username, String asignaturaId) {
        String asig = (asignaturaId == null || asignaturaId.isBlank()) ? "General" : asignaturaId;
        LocalDate hoy = LocalDate.now();

        PerfilAlumnoRegistro reg = repositorio.findByUsernameAndAsignaturaId(username, asig)
                .orElseGet(() -> PerfilAlumnoRegistro.builder().username(username).asignaturaId(asig).build());

        LocalDate ultimo = reg.getUltimoDiaActivo();
        if (ultimo != null && ultimo.equals(hoy)) {
            return new EstadoRacha(false, reg.getRachaActual(), reg.getRachaMaxima(), 0, false);
        }

        int diasAusente = (ultimo == null) ? 0 : (int) ChronoUnit.DAYS.between(ultimo, hoy);
        int nuevaRacha = (ultimo == null || diasAusente >= 2) ? 1 : reg.getRachaActual() + 1;

        reg.setRachaActual(nuevaRacha);
        reg.setRachaMaxima(Math.max(reg.getRachaMaxima(), nuevaRacha));
        reg.setUltimoDiaActivo(hoy);
        repositorio.save(reg);

        boolean mostrarBienvenida = ultimo != null && diasAusente >= DIAS_BIENVENIDA;
        return new EstadoRacha(true, nuevaRacha, reg.getRachaMaxima(), diasAusente, mostrarBienvenida);
    }
}
