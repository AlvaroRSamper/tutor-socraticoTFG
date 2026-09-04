package es.uma.tfg.tutor_socratico.servicio;

import es.uma.tfg.tutor_socratico.persistencia.RegistroConsulta;
import es.uma.tfg.tutor_socratico.persistencia.RegistroConsulta.TipoConsulta;
import es.uma.tfg.tutor_socratico.persistencia.RegistroConsultaRepositorio;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;


@Slf4j
@Service
public class ServicioRegistroConsultas {

    private final RegistroConsultaRepositorio repositorio;
    private final es.uma.tfg.tutor_socratico.perfil.PerfilAprendizajeServicio perfilAprendizajeServicio;

    public ServicioRegistroConsultas(RegistroConsultaRepositorio repositorio, es.uma.tfg.tutor_socratico.perfil.PerfilAprendizajeServicio perfilAprendizajeServicio) {
        this.repositorio = repositorio;
        this.perfilAprendizajeServicio = perfilAprendizajeServicio;
    }

    public Long registrarChat(String username, String asignaturaId, String tema, String pregunta,
                               String respuesta, String fase, int iteracion) {
        return guardar(RegistroConsulta.builder()
                .username(username)
                .asignaturaId(asignaturaId == null || asignaturaId.isBlank() ? "General" : asignaturaId)
                .fechaHora(LocalDateTime.now())
                .tema(tema)
                .tipo(TipoConsulta.CHAT)
                .pregunta(pregunta)
                .respuesta(respuesta)
                .fase(fase)
                .iteracion(iteracion)
                .build());
    }

    public Long registrarEjercicio(String username, String asignaturaId, String tema, String dificultad, String enunciado) {
        return guardar(RegistroConsulta.builder()
                .username(username)
                .asignaturaId(asignaturaId == null || asignaturaId.isBlank() ? "General" : asignaturaId)
                .fechaHora(LocalDateTime.now())
                .tema(tema)
                .tipo(TipoConsulta.EJERCICIO)
                .pregunta("Ejercicio de " + tema + " (dificultad " + dificultad + ")")
                .respuesta(enunciado)
                .build());
    }

    public boolean registrarValoracion(Long consultaId, Integer valoracion, String comentario) {
        if (consultaId == null) return false;
        return repositorio.findById(consultaId).map(reg -> {
            reg.setValoracion(valoracion);
            reg.setComentarioValoracion(comentario);
            repositorio.save(reg);
            
            if (valoracion != null) {
                es.uma.tfg.tutor_socratico.perfil.PerfilAlumno perfil = perfilAprendizajeServicio.obtenerOCrear(reg.getUsername(), reg.getAsignaturaId());
                if (valoracion == -1) {
                    perfil.setRachaNoUtil(perfil.rachaNoUtil() + 1);
                    perfil.incrementarNoUtil();
                } else if (valoracion == 1) {
                    if (perfil.isInvertido()) {
                        perfil.intercambiarPreferencias();
                    }
                    perfil.setRachaNoUtil(0);
                    perfil.incrementarUtil();
                } else {
                    perfil.setRachaNoUtil(0);
                }
                perfilAprendizajeServicio.persistir(reg.getUsername(), reg.getAsignaturaId(), perfil);
            }
            
            return true;
        }).orElse(false);
    }

    private Long guardar(RegistroConsulta registro) {
        try {
            RegistroConsulta guardado = repositorio.save(registro);
            return guardado.getId();
        } catch (Exception e) {
            log.error("No se pudo guardar el registro de consulta de {}", registro.getUsername(), e);
            return null;
        }
    }
}
