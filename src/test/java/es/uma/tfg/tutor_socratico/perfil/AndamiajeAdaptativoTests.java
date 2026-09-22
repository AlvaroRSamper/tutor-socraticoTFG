package es.uma.tfg.tutor_socratico.perfil;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AndamiajeAdaptativoTests {

    @Test
    void elNivelInicialEsIntermedio() {
        assertThat(new PerfilAlumno().nivelAndamiaje()).isEqualTo(PerfilAlumno.NIVEL_ANDAMIAJE_INICIAL);
    }

    @Test
    void dosTurnosAtascadoSubenElNivelUnaVez() {
        PerfilAlumno perfil = new PerfilAlumno();

        perfil.registrarTurnoDeCalado(true);
        assertThat(perfil.nivelAndamiaje()).isEqualTo(1);

        perfil.registrarTurnoDeCalado(true);
        assertThat(perfil.nivelAndamiaje()).isEqualTo(2);
    }

    @Test
    void tresTurnosSueltosTodaviaNoBajanElNivel() {
        PerfilAlumno perfil = new PerfilAlumno();

        perfil.registrarTurnoDeCalado(false);
        perfil.registrarTurnoDeCalado(false);
        perfil.registrarTurnoDeCalado(false);

        assertThat(perfil.nivelAndamiaje()).isEqualTo(1);
    }

    @Test
    void cuatroTurnosSueltosBajanElNivel() {
        PerfilAlumno perfil = new PerfilAlumno();

        for (int i = 0; i < 4; i++) perfil.registrarTurnoDeCalado(false);

        assertThat(perfil.nivelAndamiaje()).isEqualTo(0);
    }

    @Test
    void unTurnoAtascadoReiniciaLaCuentaDeTurnosSueltos() {
        PerfilAlumno perfil = new PerfilAlumno();

        perfil.registrarTurnoDeCalado(false);
        perfil.registrarTurnoDeCalado(false);
        perfil.registrarTurnoDeCalado(false);
        perfil.registrarTurnoDeCalado(true);
        perfil.registrarTurnoDeCalado(false);
        perfil.registrarTurnoDeCalado(false);
        perfil.registrarTurnoDeCalado(false);

        assertThat(perfil.nivelAndamiaje()).isEqualTo(1);
        assertThat(perfil.turnosSueltos()).isEqualTo(3);
    }

    @Test
    void elNivelNoSuperaElTechoNiBajaDelSuelo() {
        PerfilAlumno perfil = new PerfilAlumno();

        for (int i = 0; i < 20; i++) perfil.registrarTurnoDeCalado(true);
        assertThat(perfil.nivelAndamiaje()).isEqualTo(PerfilAlumno.NIVEL_ANDAMIAJE_MAX);

        for (int i = 0; i < 40; i++) perfil.registrarTurnoDeCalado(false);
        assertThat(perfil.nivelAndamiaje()).isEqualTo(PerfilAlumno.NIVEL_ANDAMIAJE_MIN);
    }
}
