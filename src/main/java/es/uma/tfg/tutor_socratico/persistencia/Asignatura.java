package es.uma.tfg.tutor_socratico.persistencia;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
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
public class Asignatura {

    @Id
    @Column(length = 100)
    private String asignaturaId;

    @Lob
    private String systemPrompt;

    @Lob
    private String temas;

    @Lob
    private String titulo;

    @Lob
    private String colorTema;

    private Integer sensibilidad;
}
