package es.uma.tfg.tutor_socratico.persistencia;

import es.uma.tfg.tutor_socratico.persistencia.RegistroConsulta.TipoConsulta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class RegistroConsultaRepositorioTests {

    @Autowired
    private RegistroConsultaRepositorio repositorio;

    private final LocalDateTime ayer = LocalDateTime.now().minusDays(1);
    private final LocalDateTime hoy = LocalDateTime.now();

    private RegistroConsulta consulta(String username, String tema, LocalDateTime fecha, TipoConsulta tipo) {
        return RegistroConsulta.builder()
                .username(username)
                .asignaturaId("General")
                .fechaHora(fecha)
                .tema(tema)
                .tipo(tipo)
                .pregunta("¿Qué es la herencia?")
                .respuesta("Piensa: ¿qué comparten un Coche y un Vehículo?")
                .fase(tipo == TipoConsulta.CHAT ? "CALIBRACION" : null)
                .iteracion(tipo == TipoConsulta.CHAT ? 1 : null)
                .build();
    }

    @BeforeEach
    void poblar() {
        repositorio.save(consulta("12345", "General", ayer, TipoConsulta.CHAT));
        repositorio.save(consulta("12345", "InterfacesAbstraccion.pdf", hoy, TipoConsulta.CHAT));
        repositorio.save(consulta("54321", "General", hoy, TipoConsulta.EJERCICIO));
    }

    @Test
    void sinFiltrosDevuelveTodoOrdenadoPorFechaDescendente() {
        List<RegistroConsulta> resultado = repositorio.buscarConFiltros("General", null, null, null, null, Pageable.unpaged());

        assertThat(resultado).hasSize(3);
        assertThat(resultado.get(0).getFechaHora()).isAfterOrEqualTo(resultado.get(2).getFechaHora());
    }

    @Test
    void filtraPorAlumno() {
        List<RegistroConsulta> resultado = repositorio.buscarConFiltros("General", "12345", null, null, null, Pageable.unpaged());

        assertThat(resultado).hasSize(2);
        assertThat(resultado).allMatch(r -> r.getUsername().equals("12345"));
    }

    @Test
    void filtraPorTema() {
        List<RegistroConsulta> resultado = repositorio.buscarConFiltros("General", null, "General", null, null, Pageable.unpaged());

        assertThat(resultado).hasSize(2);
    }

    @Test
    void filtraPorRangoDeFechas() {
        List<RegistroConsulta> resultado = repositorio.buscarConFiltros(
                "General", null, null, hoy.minusHours(1), null, Pageable.unpaged());

        assertThat(resultado).hasSize(2);
    }

    @Test
    void cuentaPorTema() {
        var conteos = repositorio.contarPorTema("General");

        assertThat(conteos).hasSize(2);
        assertThat(conteos.get(0).getClave()).isEqualTo("General");
        assertThat(conteos.get(0).getTotal()).isEqualTo(2);
    }

    @Test
    void cuentaPorAlumno() {
        var conteos = repositorio.contarPorAlumno("General");

        assertThat(conteos.get(0).getClave()).isEqualTo("12345");
        assertThat(conteos.get(0).getTotal()).isEqualTo(2);
    }

    @Test
    void cuentaAlumnosActivos() {
        assertThat(repositorio.contarAlumnosActivos("General")).isEqualTo(2);
    }
}
