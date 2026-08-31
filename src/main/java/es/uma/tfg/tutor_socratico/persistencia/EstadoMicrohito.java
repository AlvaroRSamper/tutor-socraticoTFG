package es.uma.tfg.tutor_socratico.persistencia;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EstadoMicrohito {

    public enum Estado { PENDIENTE, EN_PROGRESO, COMPLETADO }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private RegistroResolucion resolucion;

    @Column(nullable = false)
    private Long microhitoId;

    @Column(nullable = false)
    private int orden;

    @Column(nullable = false, length = 200)
    private String titulo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Estado estado;

    /** Autonomía del alumno en este microhito (0-100): cuánto lo resolvió sin depender de la ayuda. */
    private Integer independenciaHito;

    /**
     * Nivel máximo de ayuda que el tutor llegó a dar en este microhito, según la escala graduada:
     * 0 = solo preguntas socráticas · 1 = aclaración conceptual · 2 = pista estratégica ·
     * 3 = pseudocódigo o código. Es la señal principal de la que se deriva la autonomía.
     */
    @Builder.Default
    private Integer nivelAyudaMax = 0;

    /** Nº de intervenciones de ayuda del tutor atribuidas a este microhito (para matizar la autonomía). */
    @Builder.Default
    private Integer nAyudas = 0;

    private LocalDateTime fechaCompletado;
}
