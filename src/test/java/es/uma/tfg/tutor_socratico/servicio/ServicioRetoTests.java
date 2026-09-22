package es.uma.tfg.tutor_socratico.servicio;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import es.uma.tfg.tutor_socratico.dto.EjercicioRadarDTO;
import es.uma.tfg.tutor_socratico.dto.RespuestaEstadoReto;
import es.uma.tfg.tutor_socratico.dto.RetoPropuestoResumen;
import es.uma.tfg.tutor_socratico.excepcion.RecursoNoEncontradoException;
import es.uma.tfg.tutor_socratico.persistencia.Ejercicio;
import es.uma.tfg.tutor_socratico.persistencia.EjercicioRepositorio;
import es.uma.tfg.tutor_socratico.persistencia.EstadoMicrohito;
import es.uma.tfg.tutor_socratico.persistencia.Microhito;
import es.uma.tfg.tutor_socratico.persistencia.RegistroResolucion;
import es.uma.tfg.tutor_socratico.persistencia.RegistroResolucionRepositorio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ServicioRetoTests {

    private static final String ALUMNO = "20001";
    private static final String ASIGNATURA = "PROG1";

    private EjercicioRepositorio ejercicioRepositorio;
    private RegistroResolucionRepositorio resolucionRepositorio;
    private ServicioReto servicio;
    private Ejercicio ejercicio;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        ejercicioRepositorio = mock(EjercicioRepositorio.class);
        resolucionRepositorio = mock(RegistroResolucionRepositorio.class);
        servicio = new ServicioReto(mock(ChatLanguageModel.class), mock(ChatLanguageModel.class),
                mock(EmbeddingModel.class), mock(EmbeddingStore.class), ejercicioRepositorio, resolucionRepositorio);

        ejercicio = Ejercicio.builder().id(5L).titulo("Pila").enunciado("Implementa una pila")
                .lenguaje("java").publicado(true).asignaturaId(ASIGNATURA).build();
        ejercicio.agregarMicrohito(Microhito.builder().id(51L).orden(1).titulo("Clase Pila").build());
        ejercicio.agregarMicrohito(Microhito.builder().id(52L).orden(2).titulo("Método push").build());

        when(ejercicioRepositorio.findById(5L)).thenReturn(Optional.of(ejercicio));
        when(resolucionRepositorio.save(any(RegistroResolucion.class))).thenAnswer(inv -> {
            RegistroResolucion r = inv.getArgument(0);
            if (r.getId() == null) r.setId(99L);
            return r;
        });
    }

    private RegistroResolucion intento(Long id, RegistroResolucion.Estado estado, Integer independencia, int segundos) {
        RegistroResolucion r = RegistroResolucion.builder().id(id).username(ALUMNO).ejercicioId(5L)
                .asignaturaId(ASIGNATURA).estado(estado).porcentajeIndependencia(independencia)
                .porcentajeAutoria(90).tiempoTotalSegundos(segundos).fechaInicio(LocalDateTime.now().minusDays(1))
                .build();
        r.agregarEstadoHito(EstadoMicrohito.builder().microhitoId(51L).orden(1).titulo("Clase Pila")
                .estado(EstadoMicrohito.Estado.COMPLETADO).independenciaHito(80).build());
        r.agregarEstadoHito(EstadoMicrohito.builder().microhitoId(52L).orden(2).titulo("Método push")
                .estado(EstadoMicrohito.Estado.PENDIENTE).build());
        return r;
    }

    private void ultimoIntento(RegistroResolucion r) {
        when(resolucionRepositorio.findFirstByUsernameAndEjercicioIdOrderByFechaInicioDescIdDesc(ALUMNO, 5L))
                .thenReturn(Optional.ofNullable(r));
    }

    @Test
    void sinIntentoPrevioCreaUnoNuevoDesdeCero() {
        ultimoIntento(null);

        RespuestaEstadoReto respuesta = servicio.iniciarResolucion(5L, ALUMNO, ASIGNATURA, false);

        assertThat(respuesta.retomado()).isFalse();
        assertThat(respuesta.tiempoSegundos()).isZero();
        assertThat(respuesta.microhitos()).extracting("estado").containsOnly("PENDIENTE");
        verify(resolucionRepositorio, times(1)).save(any(RegistroResolucion.class));
    }

    @Test
    void conIntentoEnCursoLoRetomaSinCrearOtro() {
        RegistroResolucion enCurso = intento(10L, RegistroResolucion.Estado.EN_PROGRESO, 80, 600);
        ultimoIntento(enCurso);

        RespuestaEstadoReto respuesta = servicio.iniciarResolucion(5L, ALUMNO, ASIGNATURA, false);

        assertThat(respuesta.retomado()).isTrue();
        assertThat(respuesta.resolucionId()).isEqualTo(10L);
        assertThat(respuesta.tiempoSegundos()).isEqualTo(600);
        assertThat(respuesta.porcentajeIndependencia()).isEqualTo(80);
        assertThat(respuesta.microhitos()).extracting("estado").containsExactly("COMPLETADO", "PENDIENTE");
        verify(resolucionRepositorio, never()).save(any(RegistroResolucion.class));
    }

    @Test
    void reiniciarMarcaElIntentoEnCursoComoAbandonadoYEmpiezaOtro() {
        RegistroResolucion enCurso = intento(10L, RegistroResolucion.Estado.EN_PROGRESO, 80, 600);
        ultimoIntento(enCurso);

        RespuestaEstadoReto respuesta = servicio.iniciarResolucion(5L, ALUMNO, ASIGNATURA, true);

        assertThat(enCurso.getEstado()).isEqualTo(RegistroResolucion.Estado.ABANDONADO);
        assertThat(enCurso.getFechaFin()).isNotNull();
        assertThat(respuesta.retomado()).isFalse();
        assertThat(respuesta.resolucionId()).isEqualTo(99L);
        assertThat(respuesta.tiempoSegundos()).isZero();
        assertThat(respuesta.microhitos()).extracting("estado").containsOnly("PENDIENTE");
        verify(resolucionRepositorio, times(2)).save(any(RegistroResolucion.class));
    }

    @Test
    void siElUltimoIntentoEstaCompletadoEmpiezaUnoNuevo() {
        ultimoIntento(intento(10L, RegistroResolucion.Estado.COMPLETADO, 90, 900));

        RespuestaEstadoReto respuesta = servicio.iniciarResolucion(5L, ALUMNO, ASIGNATURA, false);

        assertThat(respuesta.retomado()).isFalse();
        assertThat(respuesta.resolucionId()).isEqualTo(99L);
    }

    @Test
    void listarPropuestosIndicaElEstadoYLosHitosCompletadosDelAlumno() {
        when(ejercicioRepositorio.findByAsignaturaIdAndPublicadoTrueOrderByFechaCreacionDesc(ASIGNATURA))
                .thenReturn(List.of(ejercicio));
        ultimoIntento(intento(10L, RegistroResolucion.Estado.EN_PROGRESO, 80, 600));

        RetoPropuestoResumen resumen = servicio.listarPropuestos(ALUMNO, ASIGNATURA).get(0);

        assertThat(resumen.estadoMio()).isEqualTo("EN_PROGRESO");
        assertThat(resumen.hitosCompletados()).isEqualTo(1);
        assertThat(resumen.nMicrohitos()).isEqualTo(2);
        assertThat(resumen.completadoPorMi()).isFalse();
    }

    @Test
    void unEjercicioYaCompletadoSigueMarcadoAunqueSeEmpieceOtroIntento() {
        when(ejercicioRepositorio.findByAsignaturaIdAndPublicadoTrueOrderByFechaCreacionDesc(ASIGNATURA))
                .thenReturn(List.of(ejercicio));
        ultimoIntento(intento(11L, RegistroResolucion.Estado.EN_PROGRESO, 100, 0));
        when(resolucionRepositorio.existsByUsernameAndEjercicioIdAndEstado(
                ALUMNO, 5L, RegistroResolucion.Estado.COMPLETADO)).thenReturn(true);

        RetoPropuestoResumen resumen = servicio.listarPropuestos(ALUMNO, ASIGNATURA).get(0);

        assertThat(resumen.completadoPorMi()).isTrue();
        assertThat(resumen.estadoMio()).isEqualTo("EN_PROGRESO");
    }

    @Test
    void sumarTiempoAcumulaYLimitaCadaLatido() {
        RegistroResolucion enCurso = intento(10L, RegistroResolucion.Estado.EN_PROGRESO, 80, 600);
        when(resolucionRepositorio.findById(10L)).thenReturn(Optional.of(enCurso));

        servicio.sumarTiempo(10L, 60, ALUMNO);
        servicio.sumarTiempo(10L, 100_000, ALUMNO);

        assertThat(enCurso.getTiempoTotalSegundos()).isEqualTo(600 + 60 + 300);
    }

    @Test
    void sumarTiempoNoCambiaUnIntentoTerminado() {
        RegistroResolucion completado = intento(10L, RegistroResolucion.Estado.COMPLETADO, 90, 900);
        when(resolucionRepositorio.findById(10L)).thenReturn(Optional.of(completado));

        servicio.sumarTiempo(10L, 60, ALUMNO);

        assertThat(completado.getTiempoTotalSegundos()).isEqualTo(900);
        verify(resolucionRepositorio, never()).save(any(RegistroResolucion.class));
    }

    @Test
    void sumarTiempoSobreResolucionAjenaLanzaNoEncontrado() {
        when(resolucionRepositorio.findById(10L))
                .thenReturn(Optional.of(intento(10L, RegistroResolucion.Estado.EN_PROGRESO, 80, 0)));

        assertThatThrownBy(() -> servicio.sumarTiempo(10L, 60, "99999"))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void radarPrefiereElIntentoEnCursoAlAbandonadoAunqueTengaMenosAutonomia() {
        when(ejercicioRepositorio.findByAsignaturaIdAndPublicadoTrueOrderByFechaCreacionDesc(ASIGNATURA))
                .thenReturn(List.of(ejercicio));
        RegistroResolucion abandonado = intento(10L, RegistroResolucion.Estado.ABANDONADO, 95, 300);
        RegistroResolucion enCurso = intento(11L, RegistroResolucion.Estado.EN_PROGRESO, 40, 120);
        when(resolucionRepositorio.findByAsignaturaId(ASIGNATURA)).thenReturn(List.of(abandonado, enCurso));

        EjercicioRadarDTO radar = servicio.radarDocente(ASIGNATURA).get(0);

        assertThat(radar.nAlumnos()).isEqualTo(1);
        assertThat(radar.alumnos().get(0).estado()).isEqualTo("EN_PROGRESO");
        assertThat(radar.alumnos().get(0).tiempoSegundos()).isEqualTo(120);
    }

    @Test
    void reiniciarGuardaPrimeroElAbandono() {
        RegistroResolucion enCurso = intento(10L, RegistroResolucion.Estado.EN_PROGRESO, 80, 600);
        ultimoIntento(enCurso);
        ArgumentCaptor<RegistroResolucion> guardadas = ArgumentCaptor.forClass(RegistroResolucion.class);

        servicio.iniciarResolucion(5L, ALUMNO, ASIGNATURA, true);

        verify(resolucionRepositorio, times(2)).save(guardadas.capture());
        assertThat(guardadas.getAllValues().get(0)).isSameAs(enCurso);
        assertThat(guardadas.getAllValues().get(1).getEstado()).isEqualTo(RegistroResolucion.Estado.EN_PROGRESO);
    }
}
