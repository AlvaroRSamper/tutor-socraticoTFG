package es.uma.tfg.tutor_socratico.perfil;

public class PerfilAlumno {

    public static final int NIVEL_ANDAMIAJE_MIN = 0;
    public static final int NIVEL_ANDAMIAJE_MAX = 3;
    public static final int NIVEL_ANDAMIAJE_INICIAL = 1;

    private static final int TURNOS_PARA_SUBIR = 2;
    private static final int TURNOS_PARA_BAJAR = 4;

    private int contadorTeorico = 1;
    private int contadorPractico = 1;
    private int iteracionChat = 0;
    private boolean esperandoRecalibracion = false;
    private int ultimaIteracionRecalibracion = 4;
    private int rachaNoUtil = 0;
    private int contadorUtil = 0;
    private int contadorNoUtil = 0;
    private int nivelAndamiaje = NIVEL_ANDAMIAJE_INICIAL;
    private int turnosAtascado = 0;
    private int turnosSueltos = 0;
    private Long ultimaConsultaCalado = null;

    public PerfilAlumno() {
    }

    public PerfilAlumno(int contadorTeorico, int contadorPractico) {
        this.contadorTeorico = Math.max(1, contadorTeorico);
        this.contadorPractico = Math.max(1, contadorPractico);
    }

    public PerfilAlumno(int contadorTeorico, int contadorPractico, int iteracionChat, boolean esperandoRecalibracion, int ultimaIteracionRecalibracion, int rachaNoUtil, int contadorUtil, int contadorNoUtil) {
        this.contadorTeorico = Math.max(1, contadorTeorico);
        this.contadorPractico = Math.max(1, contadorPractico);
        this.iteracionChat = Math.max(0, iteracionChat);
        this.esperandoRecalibracion = esperandoRecalibracion;
        this.ultimaIteracionRecalibracion = ultimaIteracionRecalibracion;
        this.rachaNoUtil = rachaNoUtil;
        this.contadorUtil = contadorUtil;
        this.contadorNoUtil = contadorNoUtil;
    }

    public int nivelAndamiaje() {
        return nivelAndamiaje;
    }

    public void setNivelAndamiaje(int nivelAndamiaje) {
        this.nivelAndamiaje = Math.max(NIVEL_ANDAMIAJE_MIN, Math.min(NIVEL_ANDAMIAJE_MAX, nivelAndamiaje));
    }

    public int turnosAtascado() {
        return turnosAtascado;
    }

    public void setTurnosAtascado(int turnosAtascado) {
        this.turnosAtascado = Math.max(0, turnosAtascado);
    }

    public int turnosSueltos() {
        return turnosSueltos;
    }

    public void setTurnosSueltos(int turnosSueltos) {
        this.turnosSueltos = Math.max(0, turnosSueltos);
    }

    public Long ultimaConsultaCalado() {
        return ultimaConsultaCalado;
    }

    public void setUltimaConsultaCalado(Long ultimaConsultaCalado) {
        this.ultimaConsultaCalado = ultimaConsultaCalado;
    }

    public void registrarTurnoDeCalado(boolean necesitoMasAyuda) {
        if (necesitoMasAyuda) {
            turnosSueltos = 0;
            turnosAtascado++;
            if (turnosAtascado >= TURNOS_PARA_SUBIR) {
                setNivelAndamiaje(nivelAndamiaje + 1);
                turnosAtascado = 0;
            }
        } else {
            turnosAtascado = 0;
            turnosSueltos++;
            if (turnosSueltos >= TURNOS_PARA_BAJAR) {
                setNivelAndamiaje(nivelAndamiaje - 1);
                turnosSueltos = 0;
            }
        }
    }

    public int contadorTeorico() {
        return contadorTeorico;
    }

    public int contadorPractico() {
        return contadorPractico;
    }

    public int iteracionChat() {
        return iteracionChat;
    }

    public void incrementarIteracion() {
        iteracionChat++;
    }

    public boolean esperandoRecalibracion() {
        return esperandoRecalibracion;
    }

    public void marcarEsperandoRecalibracion(boolean valor) {
        this.esperandoRecalibracion = valor;
    }

    public int ultimaIteracionRecalibracion() {
        return ultimaIteracionRecalibracion;
    }

    public void setUltimaIteracionRecalibracion(int ultimaIteracionRecalibracion) {
        this.ultimaIteracionRecalibracion = ultimaIteracionRecalibracion;
    }

    public int rachaNoUtil() {
        return rachaNoUtil;
    }

    public void setRachaNoUtil(int rachaNoUtil) {
        this.rachaNoUtil = rachaNoUtil;
    }

    public int contadorUtil() {
        return contadorUtil;
    }

    public void incrementarUtil() {
        this.contadorUtil++;
    }

    public int contadorNoUtil() {
        return contadorNoUtil;
    }

    public void incrementarNoUtil() {
        this.contadorNoUtil++;
    }

    public int porcentajeUtil() {
        int total = contadorUtil + contadorNoUtil;
        if (total == 0) return 100;
        return Math.round(100f * contadorUtil / total);
    }

    public boolean isInvertido() {
        return this.rachaNoUtil >= 2;
    }

    public void intercambiarPreferencias() {
        int temp = this.contadorTeorico;
        this.contadorTeorico = this.contadorPractico;
        this.contadorPractico = temp;
    }

    public void incrementarTeorico() {
        contadorTeorico++;
    }

    public void incrementarPractico() {
        contadorPractico++;
    }

    public int porcentajeTeorico() {
        return Math.round(100f * contadorTeorico / (contadorTeorico + contadorPractico));
    }

    public int porcentajePractico() {
        return 100 - porcentajeTeorico();
    }
}
