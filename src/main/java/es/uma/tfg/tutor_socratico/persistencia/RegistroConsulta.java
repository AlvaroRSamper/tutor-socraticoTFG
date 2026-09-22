package es.uma.tfg.tutor_socratico.persistencia;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
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
public class RegistroConsulta {

    public enum TipoConsulta { CHAT, EJERCICIO }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String username;

    @Column(length = 100)
    private String asignaturaId;

    private Integer valoracion;

    @Lob
    private String comentarioValoracion;

    @Column(nullable = false)
    private LocalDateTime fechaHora;

    @Column(length = 200)
    private String tema;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TipoConsulta tipo;

    @Lob
    @Column(nullable = false)
    private String pregunta;

    @Lob
    private String respuesta;

    @Column(length = 15)
    private String fase;

    private Integer iteracion;

    private Integer nivelRevelado;
}
