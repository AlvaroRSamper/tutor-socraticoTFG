package es.uma.tfg.tutor_socratico.perfil;

import es.uma.tfg.tutor_socratico.persistencia.PerfilAlumnoRegistro;
import es.uma.tfg.tutor_socratico.persistencia.PerfilAlumnoRepositorio;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Locale;

@Component
public class PerfilAprendizajeServicio {

    private final PerfilAlumnoRepositorio repositorio;

    public PerfilAprendizajeServicio(PerfilAlumnoRepositorio repositorio) {
        this.repositorio = repositorio;
    }

    
    public PerfilAlumno obtenerOCrear(String username, String asignaturaId) {
        String asig = normalizarAsignatura(asignaturaId);
        return repositorio.findByUsernameAndAsignaturaId(username, asig)
                .map(r -> new PerfilAlumno(r.getContadorTeorico(), r.getContadorPractico(),
                        r.getIteracionChat(), r.isEsperandoRecalibracion(), r.getUltimaIteracionRecalibracion(), r.getRachaNoUtil(), r.getContadorUtil(), r.getContadorNoUtil()))
                .orElseGet(PerfilAlumno::new);
    }

    public void registrarPreferencia(String username, String asignaturaId, String opcion) {
        String asig = normalizarAsignatura(asignaturaId);
        PerfilAlumno perfil = obtenerOCrear(username, asig);
        if ("A".equalsIgnoreCase(opcion)) {
            perfil.incrementarTeorico();
            perfil.setRachaNoUtil(0);
        } else if ("B".equalsIgnoreCase(opcion)) {
            perfil.incrementarPractico();
            perfil.setRachaNoUtil(0);
        } else {
            return;
        }
        persistir(username, asig, perfil);
    }

    
    public void aplicarHeuristicaRecalibracion(PerfilAlumno perfil, String textoLibre) {
        if (perfil == null || textoLibre == null || textoLibre.isBlank()) {
            return;
        }
        String textoNormalizado = quitarAcentos(textoLibre.toLowerCase(Locale.ROOT));
        boolean pideTeoria = textoNormalizado.contains("teor");
        boolean pidePractica = textoNormalizado.contains("pract") || textoNormalizado.contains("ejempl");
        if (pideTeoria && !pidePractica) {
            perfil.incrementarTeorico();
        } else if (pidePractica && !pideTeoria) {
            perfil.incrementarPractico();
        }
    }

    
    public void aplicarHeuristicaRecalibracion(String username, String asignaturaId, String textoLibre) {
        String asig = normalizarAsignatura(asignaturaId);
        PerfilAlumno perfil = obtenerOCrear(username, asig);
        aplicarHeuristicaRecalibracion(perfil, textoLibre);
        persistir(username, asig, perfil);
    }

    
    public void persistir(String username, String asignaturaId, PerfilAlumno perfil) {
        String asig = normalizarAsignatura(asignaturaId);
        PerfilAlumnoRegistro reg = repositorio.findByUsernameAndAsignaturaId(username, asig)
                .orElseGet(() -> PerfilAlumnoRegistro.builder()
                        .username(username)
                        .asignaturaId(asig)
                        .build());
        reg.setContadorTeorico(perfil.contadorTeorico());
        reg.setContadorPractico(perfil.contadorPractico());
        reg.setIteracionChat(perfil.iteracionChat());
        reg.setEsperandoRecalibracion(perfil.esperandoRecalibracion());
        reg.setUltimaIteracionRecalibracion(perfil.ultimaIteracionRecalibracion());
        reg.setRachaNoUtil(perfil.rachaNoUtil());
        reg.setContadorUtil(perfil.contadorUtil());
        reg.setContadorNoUtil(perfil.contadorNoUtil());
        repositorio.save(reg);
    }

    private String normalizarAsignatura(String asignaturaId) {
        return (asignaturaId == null || asignaturaId.isBlank()) ? "General" : asignaturaId;
    }

    private String quitarAcentos(String texto) {
        String normalizado = Normalizer.normalize(texto, Normalizer.Form.NFD);
        return normalizado.replaceAll("\\p{M}", "");
    }
}
