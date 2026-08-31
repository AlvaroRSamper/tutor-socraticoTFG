package es.uma.tfg.tutor_socratico.persistencia;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegistroResolucion {

    public enum Estado { EN_PROGRESO, COMPLETADO, ABANDONADO }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String username;

    @Column(nullable = false)
    private Long ejercicioId;

    @Column(length = 100)
    private String asignaturaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Estado estado;

    private Integer porcentajeIndependencia;

    private Integer porcentajeAutoria;

    @Column(columnDefinition = "text")
    private String codigoTutorAcumulado;

    private LocalDateTime fechaInicio;
    private LocalDateTime fechaFin;
    private Integer tiempoTotalSegundos;

    
    @Column(nullable = false)
    @Builder.Default
    private int nMensajesAlumno = 0;

    @Column(nullable = false)
    @Builder.Default
    private int nPistasReveladoras = 0;

    @Column(nullable = false)
    @Builder.Default
    private int nRecargas = 0;

    @OneToMany(mappedBy = "resolucion", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orden ASC")
    @Builder.Default
    private List<EstadoMicrohito> estadosHitos = new ArrayList<>();

    public void agregarEstadoHito(EstadoMicrohito estadoHito) {
        estadoHito.setResolucion(this);
        this.estadosHitos.add(estadoHito);
    }
}
