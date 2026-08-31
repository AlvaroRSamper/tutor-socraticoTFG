package es.uma.tfg.tutor_socratico.persistencia;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface RegistroConsultaRepositorio extends JpaRepository<RegistroConsulta, Long> {

    interface ConteoPorClave {
        String getClave();
        long getTotal();
    }

    @Query("""
        select r from RegistroConsulta r
        where r.asignaturaId = :asignaturaId
          and (:alumno is null or r.username = :alumno)
          and (:tema is null or r.tema = :tema)
          and (:desde is null or r.fechaHora >= :desde)
          and (:hasta is null or r.fechaHora <= :hasta)
        order by r.fechaHora desc""")
    List<RegistroConsulta> buscarConFiltros(@Param("asignaturaId") String asignaturaId,
                                            @Param("alumno") String alumno,
                                            @Param("tema") String tema,
                                            @Param("desde") LocalDateTime desde,
                                            @Param("hasta") LocalDateTime hasta,
                                            Pageable pageable);

    @Query("select r.tema as clave, count(r) as total from RegistroConsulta r where r.asignaturaId = :asignaturaId group by r.tema order by count(r) desc")
    List<ConteoPorClave> contarPorTema(@Param("asignaturaId") String asignaturaId);

    @Query("select r.username as clave, count(r) as total from RegistroConsulta r where r.asignaturaId = :asignaturaId group by r.username order by count(r) desc")
    List<ConteoPorClave> contarPorAlumno(@Param("asignaturaId") String asignaturaId);

    @Query("select count(distinct r.username) from RegistroConsulta r where r.asignaturaId = :asignaturaId")
    long contarAlumnosActivos(@Param("asignaturaId") String asignaturaId);

    long countByAsignaturaId(String asignaturaId);

    @Query("select r from RegistroConsulta r where r.asignaturaId = :asignaturaId and r.tipo = 'CHAT' order by r.fechaHora desc")
    List<RegistroConsulta> buscarUltimasConsultasChat(@Param("asignaturaId") String asignaturaId, Pageable pageable);
}
