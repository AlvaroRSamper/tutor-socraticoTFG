package es.uma.tfg.tutor_socratico.perfil;

public class PerfilAlumno {

    private int contadorTeorico = 1;
    private int contadorPractico = 1;
    private int iteracionChat = 0;
    private boolean esperandoRecalibracion = false;

    public PerfilAlumno() {
    }

    public PerfilAlumno(int contadorTeorico, int contadorPractico) {
        this.contadorTeorico = Math.max(1, contadorTeorico);
        this.contadorPractico = Math.max(1, contadorPractico);
    }

    public PerfilAlumno(int contadorTeorico, int contadorPractico, int iteracionChat, boolean esperandoRecalibracion) {
        this.contadorTeorico = Math.max(1, contadorTeorico);
        this.contadorPractico = Math.max(1, contadorPractico);
        this.iteracionChat = Math.max(0, iteracionChat);
        this.esperandoRecalibracion = esperandoRecalibracion;
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
