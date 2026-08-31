package es.uma.tfg.tutor_socratico.perfil;

import es.uma.tfg.tutor_socratico.persistencia.PerfilAlumnoRegistro;
import es.uma.tfg.tutor_socratico.persistencia.PerfilAlumnoRepositorio;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas del {@link PerfilAprendizajeServicio}. Como ahora la BD es la única fuente de verdad
 * (sin caché en memoria), se usa un repositorio "fake" con estado: {@code save} almacena y
 * {@code findByUsernameAndAsignaturaId} devuelve lo almacenado, reflejando la persistencia real.
 */
class PerfilAprendizajeServicioTests {

    private final Map<String, PerfilAlumnoRegistro> almacen = new HashMap<>();
    private final PerfilAlumnoRepositorio repositorio = crearRepositorioFake();
    private final PerfilAprendizajeServicio servicio = new PerfilAprendizajeServicio(repositorio);

    private PerfilAlumnoRepositorio crearRepositorioFake() {
        PerfilAlumnoRepositorio repo = mock(PerfilAlumnoRepositorio.class);
        when(repo.findByUsernameAndAsignaturaId(anyString(), anyString())).thenAnswer(inv ->
                Optional.ofNullable(almacen.get(clave(inv.getArgument(0), inv.getArgument(1)))));
        when(repo.save(any(PerfilAlumnoRegistro.class))).thenAnswer(inv -> {
            PerfilAlumnoRegistro r = inv.getArgument(0);
            almacen.put(clave(r.getUsername(), r.getAsignaturaId()), r);
            return r;
        });
        return repo;
    }

    private static String clave(String username, String asignaturaId) {
        return username + "|" + asignaturaId;
    }

    @Test
    void nuevoAlumnoEmpiezaEnCincuentaCincuenta() {
        PerfilAlumno perfil = servicio.obtenerOCrear("12345", "General");

        assertThat(perfil.porcentajeTeorico()).isEqualTo(50);
        assertThat(perfil.porcentajePractico()).isEqualTo(50);
        assertThat(perfil.iteracionChat()).isZero();
    }

    @Test
    void registrarPreferenciaAIncrementaTeorico() {
        servicio.registrarPreferencia("12345", "General", "A");

        PerfilAlumno perfil = servicio.obtenerOCrear("12345", "General");
        assertThat(perfil.porcentajeTeorico()).isGreaterThan(50);
    }

    @Test
    void registrarPreferenciaBIncrementaPractico() {
        servicio.registrarPreferencia("12345", "General", "B");

        PerfilAlumno perfil = servicio.obtenerOCrear("12345", "General");
        assertThat(perfil.porcentajePractico()).isGreaterThan(50);
    }

    @Test
    void heuristicaRecalibracionDetectaTeoria() {
        servicio.aplicarHeuristicaRecalibracion("12345", "General", "Prefiero que profundices más en la teoría");

        assertThat(servicio.obtenerOCrear("12345", "General").porcentajeTeorico()).isGreaterThan(50);
    }

    @Test
    void heuristicaRecalibracionDetectaPractica() {
        servicio.aplicarHeuristicaRecalibracion("12345", "General", "Prefiero más ejemplos prácticos");

        assertThat(servicio.obtenerOCrear("12345", "General").porcentajePractico()).isGreaterThan(50);
    }

    @Test
    void heuristicaRecalibracionAmbiguaNoModificaElPerfil() {
        servicio.aplicarHeuristicaRecalibracion("12345", "General", "no lo tengo claro todavía");

        PerfilAlumno perfil = servicio.obtenerOCrear("12345", "General");
        assertThat(perfil.porcentajeTeorico()).isEqualTo(50);
        assertThat(perfil.porcentajePractico()).isEqualTo(50);
    }

    @Test
    void losUsuariosNoComparenEstado() {
        servicio.registrarPreferencia("12345", "General", "A");
        servicio.registrarPreferencia("12345", "General", "A");

        PerfilAlumno otroAlumno = servicio.obtenerOCrear("54321", "General");
        assertThat(otroAlumno.porcentajeTeorico()).isEqualTo(50);
    }

    @Test
    void registrarPreferenciaPersisteLosContadores() {
        servicio.registrarPreferencia("12345", "General", "A");

        verify(repositorio).save(any(PerfilAlumnoRegistro.class));
    }

    @Test
    void laIteracionYRecalibracionSePersistenYSeRestauran() {
        PerfilAlumno perfil = servicio.obtenerOCrear("12345", "General");
        perfil.incrementarIteracion();
        perfil.incrementarIteracion();
        perfil.marcarEsperandoRecalibracion(true);
        servicio.persistir("12345", "General", perfil);

        PerfilAlumno recargado = servicio.obtenerOCrear("12345", "General");
        assertThat(recargado.iteracionChat()).isEqualTo(2);
        assertThat(recargado.esperandoRecalibracion()).isTrue();
    }

    @Test
    void elPerfilGuardadoSeRestauraDesdeBaseDeDatos() {
        PerfilAlumnoRepositorio repoConDatos = mock(PerfilAlumnoRepositorio.class);
        when(repoConDatos.findByUsernameAndAsignaturaId("12345", "General")).thenReturn(Optional.of(
                PerfilAlumnoRegistro.builder()
                        .id(1L).username("12345").asignaturaId("General").contadorTeorico(3).contadorPractico(1).build()));
        PerfilAprendizajeServicio servicioNuevo = new PerfilAprendizajeServicio(repoConDatos);

        PerfilAlumno perfil = servicioNuevo.obtenerOCrear("12345", "General");

        assertThat(perfil.porcentajeTeorico()).isEqualTo(75);
        assertThat(perfil.porcentajePractico()).isEqualTo(25);
    }
}
