package es.uma.tfg.tutor_socratico.perfil;

public class PerfilAlumno {

    private int contadorTeorico = 1;
    private int contadorPractico = 1;
    private int iteracionChat = 0;
    private boolean esperandoRecalibracion = false;
    private int ultimaIteracionRecalibracion = 4;
    private int rachaNoUtil = 0;
    private int contadorUtil = 0;
    private int contadorNoUtil = 0;

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
