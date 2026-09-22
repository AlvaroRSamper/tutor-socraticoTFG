package es.uma.tfg.tutor_socratico.servicio;

import es.uma.tfg.tutor_socratico.dto.TendenciaTema;
import es.uma.tfg.tutor_socratico.persistencia.RegistroConsulta;
import es.uma.tfg.tutor_socratico.persistencia.RegistroConsulta.TipoConsulta;
import es.uma.tfg.tutor_socratico.persistencia.RegistroConsultaRepositorio;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


@Slf4j
@Service
public class ServicioRegistroConsultas {

    private static final int VENTANA_TENDENCIA = 5;
    private static final int MIN_MUESTRAS_POR_BLOQUE = 3;
    private static final int MAX_TURNOS_ANALIZADOS = 200;
    private static final double UMBRAL_TENDENCIA = 0.3;

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

    public boolean necesitoMasAyuda(Long consultaId) {
        if (consultaId == null) return false;
        return repositorio.findById(consultaId).map(reg -> {
            boolean destapoCapas = reg.getNivelRevelado() != null && reg.getNivelRevelado() > 0;
            boolean valoroMejorable = reg.getValoracion() != null && reg.getValoracion() == -1;
            return destapoCapas || valoroMejorable;
        }).orElse(false);
    }

    public void marcarTurnoDeCalado(Long consultaId) {
        if (consultaId == null) return;
        repositorio.findById(consultaId).ifPresent(reg -> {
            if (reg.getNivelRevelado() == null) {
                reg.setNivelRevelado(0);
                repositorio.save(reg);
            }
        });
    }

    public boolean registrarRevelacion(Long consultaId, Integer nivel, String username) {
        if (consultaId == null || nivel == null) return false;
        return repositorio.findById(consultaId).map(reg -> {
            if (!reg.getUsername().equals(username)) return false;
            int actual = reg.getNivelRevelado() != null ? reg.getNivelRevelado() : 0;
            if (nivel > actual) {
                reg.setNivelRevelado(nivel);
                repositorio.save(reg);
            }
            return true;
        }).orElse(false);
    }

    public List<TendenciaTema> tendenciasPorTema(String username, String asignaturaId) {
        String asig = (asignaturaId == null || asignaturaId.isBlank()) ? "General" : asignaturaId;
        List<RegistroConsulta> turnos = repositorio.buscarTurnosDeCalado(
                username, asig, PageRequest.of(0, MAX_TURNOS_ANALIZADOS));

        Map<String, List<Integer>> porTema = new LinkedHashMap<>();
        for (RegistroConsulta r : turnos) {
            String tema = (r.getTema() == null || r.getTema().isBlank()) ? "General" : r.getTema();
            porTema.computeIfAbsent(tema, k -> new ArrayList<>()).add(r.getNivelRevelado());
        }

        List<TendenciaTema> salida = new ArrayList<>();
        for (Map.Entry<String, List<Integer>> entrada : porTema.entrySet()) {
            TendenciaTema tendencia = calcularTendencia(entrada.getKey(), entrada.getValue());
            if (tendencia != null) salida.add(tendencia);
        }
        return salida;
    }

    private TendenciaTema calcularTendencia(String tema, List<Integer> nivelesRecientesPrimero) {
        int total = nivelesRecientesPrimero.size();
        if (total < MIN_MUESTRAS_POR_BLOQUE * 2) return null;

        int corteReciente = Math.min(VENTANA_TENDENCIA, total - MIN_MUESTRAS_POR_BLOQUE);
        List<Integer> recientes = nivelesRecientesPrimero.subList(0, corteReciente);
        int finPrevio = Math.min(total, corteReciente + VENTANA_TENDENCIA);
        List<Integer> previos = nivelesRecientesPrimero.subList(corteReciente, finPrevio);

        if (recientes.size() < MIN_MUESTRAS_POR_BLOQUE || previos.size() < MIN_MUESTRAS_POR_BLOQUE) return null;

        double mediaReciente = media(recientes);
        double mediaPrevia = media(previos);
        double diferencia = mediaReciente - mediaPrevia;

        int direccion = 0;
        if (diferencia <= -UMBRAL_TENDENCIA) direccion = 1;
        else if (diferencia >= UMBRAL_TENDENCIA) direccion = -1;

        return new TendenciaTema(tema, direccion,
                redondear(mediaReciente), redondear(mediaPrevia), recientes.size() + previos.size());
    }

    private double media(List<Integer> valores) {
        int suma = 0;
        for (Integer v : valores) suma += (v != null ? v : 0);
        return (double) suma / valores.size();
    }

    private double redondear(double valor) {
        return Math.round(valor * 10.0) / 10.0;
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
