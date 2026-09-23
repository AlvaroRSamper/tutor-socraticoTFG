package es.uma.tfg.tutor_socratico.controlador;

import es.uma.tfg.tutor_socratico.persistencia.Ejercicio;
import es.uma.tfg.tutor_socratico.persistencia.EjercicioRepositorio;
import es.uma.tfg.tutor_socratico.persistencia.Microhito;
import es.uma.tfg.tutor_socratico.persistencia.RegistroResolucion;
import es.uma.tfg.tutor_socratico.persistencia.RegistroResolucionRepositorio;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ciclo de vida de un reto propuesto con el servicio real (sin mocks) y open-in-view desactivado,
 * que es como corre en producción: el alumno lo ve con sus microhitos —contarlos toca una colección
 * perezosa, así que esto falla si listarPropuestos pierde su transacción— y el profesor lo borra,
 * llevándose por delante el progreso de quien lo hubiera empezado.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RetosPropuestosIntegracionTests {

    private static final String ASIGNATURA = "General";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EjercicioRepositorio ejercicioRepositorio;

    @Autowired
    private RegistroResolucionRepositorio resolucionRepositorio;

    private Long ejercicioId;

    @AfterEach
    void limpiar() {
        if (ejercicioId != null) {
            resolucionRepositorio.deleteAll(resolucionRepositorio.findByEjercicioId(ejercicioId));
            if (ejercicioRepositorio.existsById(ejercicioId)) {
                ejercicioRepositorio.deleteById(ejercicioId);
            }
            ejercicioId = null;
        }
    }

    private Long empezarloComoAlumno(String alumno) {
        return resolucionRepositorio.save(RegistroResolucion.builder()
                .username(alumno)
                .ejercicioId(ejercicioId)
                .asignaturaId(ASIGNATURA)
                .estado(RegistroResolucion.Estado.EN_PROGRESO)
                .fechaInicio(LocalDateTime.now())
                .tiempoTotalSegundos(0)
                .build()).getId();
    }

    private void publicarReto() {
        Ejercicio ejercicio = Ejercicio.builder()
                .titulo("RETO 1")
                .enunciado("Implementa una lista enlazada.")
                .origen(Ejercicio.Origen.PROPUESTO)
                .dificultad("Fácil")
                .tema("Estructuras de datos")
                .asignaturaId(ASIGNATURA)
                .lenguaje("c")
                .autorUsername("99991")
                .publicado(true)
                .fechaCreacion(LocalDateTime.now())
                .build();
        ejercicio.agregarMicrohito(Microhito.builder().orden(1).titulo("Definir el struct").build());
        ejercicio.agregarMicrohito(Microhito.builder().orden(2).titulo("Insertar al final").build());
        ejercicioId = ejercicioRepositorio.save(ejercicio).getId();
    }

    @Test
    void elAlumnoVeElRetoQueHaPublicadoSuProfesorConSusMicrohitos() throws Exception {
        publicarReto();

        mockMvc.perform(get("/api/reto/propuestos").with(user("12345").roles("ALUMNO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ejercicioId").value(ejercicioId))
                .andExpect(jsonPath("$[0].titulo").value("RETO 1"))
                .andExpect(jsonPath("$[0].nMicrohitos").value(2))
                .andExpect(jsonPath("$[0].completadoPorMi").value(false));
    }

    @Test
    void elProfesorBorraUnRetoYDejaDeVerseEnElPanelDelAlumno() throws Exception {
        publicarReto();

        mockMvc.perform(delete("/api/reto/profesor/ejercicio/" + ejercicioId)
                        .with(user("99991").roles("PROFESOR"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exito").value(true));

        assertThat(ejercicioRepositorio.existsById(ejercicioId)).isFalse();

        mockMvc.perform(get("/api/reto/propuestos").with(user("12345").roles("ALUMNO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void borrarUnRetoSeLlevaTambienElProgresoDeLosAlumnos() throws Exception {
        publicarReto();
        Long resolucionId = empezarloComoAlumno("12345");

        mockMvc.perform(delete("/api/reto/profesor/ejercicio/" + ejercicioId)
                        .with(user("99991").roles("PROFESOR"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value(org.hamcrest.Matchers.containsString("1 alumno")));

        assertThat(resolucionRepositorio.existsById(resolucionId)).isFalse();
    }

    @Test
    void noSePuedeBorrarUnRetoDeOtraAsignatura() throws Exception {
        publicarReto();
        Ejercicio ajeno = ejercicioRepositorio.findById(ejercicioId).orElseThrow();
        ajeno.setAsignaturaId("OTRA-ASIGNATURA");
        ejercicioRepositorio.save(ajeno);

        mockMvc.perform(delete("/api/reto/profesor/ejercicio/" + ejercicioId)
                        .with(user("99991").roles("PROFESOR"))
                        .with(csrf()))
                .andExpect(status().isNotFound());

        assertThat(ejercicioRepositorio.existsById(ejercicioId)).isTrue();
    }

    @Test
    void unAlumnoNoPuedeBorrarRetos() throws Exception {
        publicarReto();

        mockMvc.perform(delete("/api/reto/profesor/ejercicio/" + ejercicioId)
                        .with(user("12345").roles("ALUMNO"))
                        .with(csrf()))
                .andExpect(status().isForbidden());

        assertThat(ejercicioRepositorio.existsById(ejercicioId)).isTrue();
    }
}
