package es.uma.tfg.tutor_socratico.persistencia;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PerfilAlumnoRegistro {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String username;

    @Column(length = 100)
    private String asignaturaId;

    @Column(nullable = false)
    private int contadorTeorico;

    @Column(nullable = false)
    private int contadorPractico;

    
    @Column(nullable = false, columnDefinition = "integer not null default 0")
    private int iteracionChat;

    
    @Column(nullable = false, columnDefinition = "boolean not null default false")
    private boolean esperandoRecalibracion;
}
