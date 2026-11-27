package com.cliniara.api.domain.prontuario;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProntuarioRepository extends JpaRepository<Prontuario, Long> {

    Page<Prontuario> findAllByAtivoTrue(Pageable pageable);

    Page<Prontuario> findAllByAtivoTrueAndMedicoId(Long medicoId, Pageable pageable);

    Page<Prontuario> findAllByAtivoTrueAndPacienteId(Long pacienteId, Pageable pageable);

    Page<Prontuario> findAllByAtivoTrueAndMedicoIdAndPacienteId(Long medicoId, Long pacienteId, Pageable pageable);

    @Query(
            value = """
                    SELECT p FROM Prontuario p
                    JOIN FETCH p.paciente pac
                    WHERE p.ativo = true
                      AND (
                           LOWER(pac.nome) LIKE :termo ESCAPE '!'
                           OR LOWER(p.diagnostico) LIKE :termo ESCAPE '!'
                           OR LOWER(COALESCE(p.cid10, '')) LIKE :termo ESCAPE '!'
                      )
                    """,
            countQuery = """
                    SELECT COUNT(p) FROM Prontuario p
                    JOIN p.paciente pac
                    WHERE p.ativo = true
                      AND (
                           LOWER(pac.nome) LIKE :termo ESCAPE '!'
                           OR LOWER(p.diagnostico) LIKE :termo ESCAPE '!'
                           OR LOWER(COALESCE(p.cid10, '')) LIKE :termo ESCAPE '!'
                      )
                    """
    )
    Page<Prontuario> findAllAtivosComBusca(@Param("termo") String termo, Pageable pageable);

    @Query(
            value = """
                    SELECT p FROM Prontuario p
                    JOIN FETCH p.paciente pac
                    WHERE p.ativo = true
                      AND p.medico.id = :medicoId
                      AND (
                           LOWER(pac.nome) LIKE :termo ESCAPE '!'
                           OR LOWER(p.diagnostico) LIKE :termo ESCAPE '!'
                           OR LOWER(COALESCE(p.cid10, '')) LIKE :termo ESCAPE '!'
                      )
                    """,
            countQuery = """
                    SELECT COUNT(p) FROM Prontuario p
                    JOIN p.paciente pac
                    WHERE p.ativo = true
                      AND p.medico.id = :medicoId
                      AND (
                           LOWER(pac.nome) LIKE :termo ESCAPE '!'
                           OR LOWER(p.diagnostico) LIKE :termo ESCAPE '!'
                           OR LOWER(COALESCE(p.cid10, '')) LIKE :termo ESCAPE '!'
                      )
                    """
    )
    Page<Prontuario> findAllAtivosByMedicoIdComBusca(
            @Param("medicoId") Long medicoId,
            @Param("termo") String termo,
            Pageable pageable
    );

    long countByAtivoTrueAndMedicoId(Long medicoId);

    boolean existsByConsultaId(Long consultaId);
}
