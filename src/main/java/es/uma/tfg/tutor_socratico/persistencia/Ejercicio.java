package es.uma.tfg.tutor_socratico.persistencia;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
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
public class Ejercicio {

    public enum Origen { PROPIO, GENERADO_IA, PROPUESTO }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String titulo;

    @Lob
    @Column(nullable = false)
    private String enunciado;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Origen origen;

    @Column(length = 20)
    private String dificultad;

    @Column(length = 200)
    private String tema;

    @Column(length = 100)
    private String asignaturaId;

    @Column(length = 20)
    private String lenguaje;

    @Column(length = 20)
    private String autorUsername;

    @Column(nullable = false)
    private boolean publicado;

    @Column(nullable = false)
    private LocalDateTime fechaCreacion;

    @OneToMany(mappedBy = "ejercicio", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orden ASC")
    @Builder.Default
    private List<Microhito> microhitos = new ArrayList<>();

    public void agregarMicrohito(Microhito microhito) {
        microhito.setEjercicio(this);
        this.microhitos.add(microhito);
    }
}
