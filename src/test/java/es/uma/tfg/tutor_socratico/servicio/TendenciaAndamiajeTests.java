package es.uma.tfg.tutor_socratico.servicio;

import es.uma.tfg.tutor_socratico.dto.TendenciaTema;
import es.uma.tfg.tutor_socratico.perfil.PerfilAprendizajeServicio;
import es.uma.tfg.tutor_socratico.persistencia.RegistroConsulta;
import es.uma.tfg.tutor_socratico.persistencia.RegistroConsultaRepositorio;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TendenciaAndamiajeTests {

    private final RegistroConsultaRepositorio repositorio = mock(RegistroConsultaRepositorio.class);
    private final PerfilAprendizajeServicio perfilServicio = mock(PerfilAprendizajeServicio.class);
    private final ServicioRegistroConsultas servicio = new ServicioRegistroConsultas(repositorio, perfilServicio);

    @Test
    void marcaTendenciaAlAlzaCuandoElAlumnoNecesitaMenosPistaQueAntes() {
        prepararTurnos("Tema1.pdf", 0, 0, 1, 0, 0, 2, 2, 1, 2, 2);

        List<TendenciaTema> tendencias = servicio.tendenciasPorTema("12345", "General");

        assertThat(tendencias).hasSize(1);
        assertThat(tendencias.get(0).tema()).isEqualTo("Tema1.pdf");
        assertThat(tendencias.get(0).direccion()).isEqualTo(1);
        assertThat(tendencias.get(0).mediaReciente()).isEqualTo(0.2);
        assertThat(tendencias.get(0).mediaPrevia()).isEqualTo(1.8);
    }

    @Test
    void marcaTendenciaALaBajaCuandoElAlumnoNecesitaMasPistaQueAntes() {
        prepararTurnos("Tema2.pdf", 2, 2, 2, 1, 2, 0, 0, 0, 1, 0);

        List<TendenciaTema> tendencias = servicio.tendenciasPorTema("12345", "General");

        assertThat(tendencias).hasSize(1);
        assertThat(tendencias.get(0).direccion()).isEqualTo(-1);
    }

    @Test
    void noMuestraTendenciaSiLaDiferenciaEsIrrelevante() {
        prepararTurnos("Tema3.pdf", 1, 1, 1, 1, 1, 1, 1, 1, 1, 1);

        List<TendenciaTema> tendencias = servicio.tendenciasPorTema("12345", "General");

        assertThat(tendencias).hasSize(1);
        assertThat(tendencias.get(0).direccion()).isZero();
    }

    @Test
    void noDevuelveTendenciaSinMuestrasSuficientes() {
        prepararTurnos("Tema4.pdf", 2, 2, 0, 0, 0);

        assertThat(servicio.tendenciasPorTema("12345", "General")).isEmpty();
    }

    @Test
    void separaLaTendenciaDeCadaTema() {
        List<RegistroConsulta> turnos = new ArrayList<>();
        turnos.addAll(construir("Tema1.pdf", 0, 0, 0, 2, 2, 2));
        turnos.addAll(construir("Tema2.pdf", 2, 2, 2, 0, 0, 0));
        when(repositorio.buscarTurnosDeCalado(eq("12345"), eq("General"), any())).thenReturn(turnos);

        List<TendenciaTema> tendencias = servicio.tendenciasPorTema("12345", "General");

        assertThat(tendencias).hasSize(2);
        assertThat(tendencias).anySatisfy(t -> {
            assertThat(t.tema()).isEqualTo("Tema1.pdf");
            assertThat(t.direccion()).isEqualTo(1);
        });
        assertThat(tendencias).anySatisfy(t -> {
            assertThat(t.tema()).isEqualTo("Tema2.pdf");
            assertThat(t.direccion()).isEqualTo(-1);
        });
    }

    private void prepararTurnos(String tema, int... niveles) {
        when(repositorio.buscarTurnosDeCalado(eq("12345"), eq("General"), any()))
                .thenReturn(construir(tema, niveles));
    }

    private List<RegistroConsulta> construir(String tema, int... niveles) {
        List<RegistroConsulta> turnos = new ArrayList<>();
        LocalDateTime ahora = LocalDateTime.now();
        for (int i = 0; i < niveles.length; i++) {
            turnos.add(RegistroConsulta.builder()
                    .id((long) (turnos.size() + 1))
                    .username("12345")
                    .asignaturaId("General")
                    .tema(tema)
                    .tipo(RegistroConsulta.TipoConsulta.CHAT)
                    .pregunta("pregunta " + i)
                    .fechaHora(ahora.minusHours(i))
                    .nivelRevelado(niveles[i])
                    .build());
        }
        return turnos;
    }
}
