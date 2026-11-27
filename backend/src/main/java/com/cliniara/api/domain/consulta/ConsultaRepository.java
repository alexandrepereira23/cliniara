package com.cliniara.api.domain.consulta;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

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
                JOIN FETCH c.medico
                JOIN FETCH c.paciente
                WHERE (:status IS NULL OR c.status = :status)
                  AND (:status IS NOT NULL OR :incluirEncerradas = true OR c.ativo = true)
                """,
            countQuery = """
                SELECT COUNT(c) FROM Consulta c
                WHERE (:status IS NULL OR c.status = :status)
                  AND (:status IS NOT NULL OR :incluirEncerradas = true OR c.ativo = true)
                """
    )
    Page<Consulta> findAllWithFilters(
            @Param("status") StatusConsulta status,
            @Param("incluirEncerradas") boolean incluirEncerradas,
            Pageable paginacao
    );

    @Query(
            value = """
                SELECT c FROM Consulta c
                JOIN FETCH c.medico m
                JOIN FETCH c.paciente
                WHERE m.usuario.id = :usuarioId
                  AND (:status IS NULL OR c.status = :status)
                  AND (:status IS NOT NULL OR :incluirEncerradas = true OR c.ativo = true)
                """,
            countQuery = """
                SELECT COUNT(c) FROM Consulta c
                JOIN c.medico m
                WHERE m.usuario.id = :usuarioId
                  AND (:status IS NULL OR c.status = :status)
                  AND (:status IS NOT NULL OR :incluirEncerradas = true OR c.ativo = true)
                """
    )
    Page<Consulta> findAllByMedicoUsuarioIdWithFilters(
            @Param("usuarioId") Long usuarioId,
            @Param("status") StatusConsulta status,
            @Param("incluirEncerradas") boolean incluirEncerradas,
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
}
