package com.cliniara.api.domain.consulta;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ConsultaRepository extends JpaRepository<Consulta, Long> {

    boolean existsByPacienteIdAndDataHoraBetweenAndAtivoTrue(Long idPaciente, LocalDateTime primeiroHorario, LocalDateTime ultimoHorario);

    boolean existsByMedicoIdAndDataHoraAndAtivoTrue(Long idMedico, LocalDateTime dataHora);

    boolean existsByConsultaOrigemId(Long consultaOrigemId);

    boolean existsByConsultaOrigemIdAndAtivoTrue(Long consultaOrigemId);

    @Query(
            value = "SELECT c FROM Consulta c JOIN FETCH c.medico JOIN FETCH c.paciente WHERE c.ativo = true",
            countQuery = "SELECT COUNT(c) FROM Consulta c WHERE c.ativo = true"
    )
    Page<Consulta> findAllByAtivoTrue(Pageable paginacao);

    @Query(
            value = """
                SELECT c FROM Consulta c
                JOIN FETCH c.medico m
                JOIN FETCH c.paciente
                WHERE c.ativo = true AND m.usuario.id = :usuarioId
                """,
            countQuery = """
                SELECT COUNT(c) FROM Consulta c
                JOIN c.medico m
                WHERE c.ativo = true AND m.usuario.id = :usuarioId
                """
    )
    Page<Consulta> findAllByAtivoTrueAndMedicoUsuarioId(Pageable paginacao, @Param("usuarioId") Long usuarioId);

    @Query(
            value = """
                SELECT c FROM Consulta c
                JOIN FETCH c.medico m
                JOIN FETCH c.paciente p
                WHERE (:status IS NULL OR c.status = :status)
                  AND (:status IS NOT NULL OR :incluirEncerradas = true OR c.ativo = true)
                  AND (:medicoId IS NULL OR m.id = :medicoId)
                  AND (:inicio IS NULL OR c.dataHora >= :inicio)
                  AND (:fimExclusivo IS NULL OR c.dataHora < :fimExclusivo)
                  AND (
                       :termo IS NULL
                       OR LOWER(p.nome) LIKE :termo ESCAPE '!'
                       OR LOWER(m.nome) LIKE :termo ESCAPE '!'
                  )
                """,
            countQuery = """
                SELECT COUNT(c) FROM Consulta c
                JOIN c.medico m
                JOIN c.paciente p
                WHERE (:status IS NULL OR c.status = :status)
                  AND (:status IS NOT NULL OR :incluirEncerradas = true OR c.ativo = true)
                  AND (:medicoId IS NULL OR m.id = :medicoId)
                  AND (:inicio IS NULL OR c.dataHora >= :inicio)
                  AND (:fimExclusivo IS NULL OR c.dataHora < :fimExclusivo)
                  AND (
                       :termo IS NULL
                       OR LOWER(p.nome) LIKE :termo ESCAPE '!'
                       OR LOWER(m.nome) LIKE :termo ESCAPE '!'
                  )
                """
    )
    Page<Consulta> findAllWithFilters(
            @Param("status") StatusConsulta status,
            @Param("incluirEncerradas") boolean incluirEncerradas,
            @Param("termo") String termo,
            @Param("medicoId") Long medicoId,
            @Param("inicio") LocalDateTime inicio,
            @Param("fimExclusivo") LocalDateTime fimExclusivo,
            Pageable paginacao
    );

    @Query(
            value = """
                SELECT c FROM Consulta c
                JOIN FETCH c.medico m
                JOIN FETCH c.paciente p
                WHERE m.usuario.id = :usuarioId
                  AND (:status IS NULL OR c.status = :status)
                  AND (:status IS NOT NULL OR :incluirEncerradas = true OR c.ativo = true)
                  AND (:inicio IS NULL OR c.dataHora >= :inicio)
                  AND (:fimExclusivo IS NULL OR c.dataHora < :fimExclusivo)
                  AND (
                       :termo IS NULL
                       OR LOWER(p.nome) LIKE :termo ESCAPE '!'
                       OR LOWER(m.nome) LIKE :termo ESCAPE '!'
                  )
                """,
            countQuery = """
                SELECT COUNT(c) FROM Consulta c
                JOIN c.medico m
                JOIN c.paciente p
                WHERE m.usuario.id = :usuarioId
                  AND (:status IS NULL OR c.status = :status)
                  AND (:status IS NOT NULL OR :incluirEncerradas = true OR c.ativo = true)
                  AND (:inicio IS NULL OR c.dataHora >= :inicio)
                  AND (:fimExclusivo IS NULL OR c.dataHora < :fimExclusivo)
                  AND (
                       :termo IS NULL
                       OR LOWER(p.nome) LIKE :termo ESCAPE '!'
                       OR LOWER(m.nome) LIKE :termo ESCAPE '!'
                  )
                """
    )
    Page<Consulta> findAllByMedicoUsuarioIdWithFilters(
            @Param("usuarioId") Long usuarioId,
            @Param("status") StatusConsulta status,
            @Param("incluirEncerradas") boolean incluirEncerradas,
            @Param("termo") String termo,
            @Param("inicio") LocalDateTime inicio,
            @Param("fimExclusivo") LocalDateTime fimExclusivo,
            Pageable paginacao
    );

    @Query("""
        SELECT COUNT(c) FROM Consulta c
        WHERE c.medico.id = :medicoId
          AND (
               c.status = com.cliniara.api.domain.consulta.StatusConsulta.EM_ATENDIMENTO
               OR (c.status IN (
                       com.cliniara.api.domain.consulta.StatusConsulta.AGENDADA,
                       com.cliniara.api.domain.consulta.StatusConsulta.CONFIRMADA,
                       com.cliniara.api.domain.consulta.StatusConsulta.CHECK_IN
                   ) AND c.dataHora >= :agora)
          )
        """)
    long countConsultasPendentesOuFuturasDoMedico(
            @Param("medicoId") Long medicoId,
            @Param("agora") LocalDateTime agora
    );

    // ── queries agregadas para dashboards ─────────────────────────────────────

    @Query("""
        SELECT c FROM Consulta c
        JOIN FETCH c.medico
        JOIN FETCH c.paciente
        WHERE c.ativo = true
          AND c.dataHora >= :inicio
          AND c.dataHora < :fimExclusivo
        ORDER BY c.dataHora ASC
        """)
    List<Consulta> findAllAtivasNoIntervalo(
            @Param("inicio") LocalDateTime inicio,
            @Param("fimExclusivo") LocalDateTime fimExclusivo
    );

    @Query("""
        SELECT c FROM Consulta c
        JOIN FETCH c.medico m
        JOIN FETCH c.paciente
        WHERE c.ativo = true
          AND m.id = :medicoId
          AND c.dataHora >= :inicio
          AND c.dataHora < :fimExclusivo
        ORDER BY c.dataHora ASC
        """)
    List<Consulta> findAllAtivasNoIntervaloByMedicoId(
            @Param("medicoId") Long medicoId,
            @Param("inicio") LocalDateTime inicio,
            @Param("fimExclusivo") LocalDateTime fimExclusivo
    );

    @Query("""
        SELECT c.status, COUNT(c) FROM Consulta c
        WHERE c.dataHora >= :inicio
          AND c.dataHora < :fimExclusivo
        GROUP BY c.status
        """)
    List<Object[]> countByStatusNoIntervalo(
            @Param("inicio") LocalDateTime inicio,
            @Param("fimExclusivo") LocalDateTime fimExclusivo
    );

    @Query("""
        SELECT e.nome,
               COUNT(c),
               SUM(CASE WHEN c.status = com.cliniara.api.domain.consulta.StatusConsulta.CONCLUIDA THEN 1L ELSE 0L END),
               SUM(CASE WHEN c.status = com.cliniara.api.domain.consulta.StatusConsulta.CANCELADA THEN 1L ELSE 0L END),
               SUM(CASE WHEN c.status = com.cliniara.api.domain.consulta.StatusConsulta.AUSENTE THEN 1L ELSE 0L END)
        FROM Consulta c
        JOIN c.medico m
        JOIN m.especialidade e
        WHERE c.dataHora >= :inicio
          AND c.dataHora < :fimExclusivo
        GROUP BY e.nome
        ORDER BY COUNT(c) DESC
        """)
    List<Object[]> countProducaoPorEspecialidadeNoIntervalo(
            @Param("inicio") LocalDateTime inicio,
            @Param("fimExclusivo") LocalDateTime fimExclusivo
    );

    long countByMedicoIdAndAtivoTrue(Long medicoId);

    long countByMedicoIdAndAtivoTrueAndDataHoraGreaterThanEqualAndDataHoraLessThan(
            Long medicoId,
            LocalDateTime inicio,
            LocalDateTime fimExclusivo
    );

    @Query("""
        SELECT COUNT(DISTINCT c.paciente.id) FROM Consulta c
        WHERE c.ativo = true
          AND c.paciente.ativo = true
          AND c.medico.id = :medicoId
        """)
    long countPacientesDistintosAtivosByMedicoId(@Param("medicoId") Long medicoId);
}
