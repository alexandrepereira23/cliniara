package com.cliniara.api.domain.paciente;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PacienteRepository extends JpaRepository<Paciente, Long> {

    Page<Paciente> findAllByAtivoTrue(Pageable paginacao);

    long countByAtivoTrue();

    @Query(
            value = """
                    SELECT p FROM Paciente p
                    WHERE p.ativo = true
                      AND (
                           LOWER(p.nome) LIKE :termo ESCAPE '!'
                           OR LOWER(p.email) LIKE :termo ESCAPE '!'
                           OR LOWER(p.cpf) LIKE :termo ESCAPE '!'
                           OR (:termoDigitos IS NOT NULL AND p.cpf LIKE :termoDigitos ESCAPE '!')
                      )
                    """,
            countQuery = """
                    SELECT COUNT(p) FROM Paciente p
                    WHERE p.ativo = true
                      AND (
                           LOWER(p.nome) LIKE :termo ESCAPE '!'
                           OR LOWER(p.email) LIKE :termo ESCAPE '!'
                           OR LOWER(p.cpf) LIKE :termo ESCAPE '!'
                           OR (:termoDigitos IS NOT NULL AND p.cpf LIKE :termoDigitos ESCAPE '!')
                      )
                    """
    )
    Page<Paciente> findAllAtivosComBusca(
            @Param("termo") String termo,
            @Param("termoDigitos") String termoDigitos,
            Pageable paginacao
    );

    @Query(
            value = """
                    SELECT DISTINCT p FROM Consulta c
                    JOIN c.paciente p
                    JOIN c.medico m
                    WHERE p.ativo = true
                      AND c.ativo = true
                      AND m.usuario.id = :usuarioId
                    """,
            countQuery = """
                    SELECT COUNT(DISTINCT p.id) FROM Consulta c
                    JOIN c.paciente p
                    JOIN c.medico m
                    WHERE p.ativo = true
                      AND c.ativo = true
                      AND m.usuario.id = :usuarioId
                    """
    )
    Page<Paciente> findAllAtivosVinculadosAoMedico(Pageable paginacao, @Param("usuarioId") Long usuarioId);

    @Query(
            value = """
                    SELECT DISTINCT p FROM Consulta c
                    JOIN c.paciente p
                    JOIN c.medico m
                    WHERE p.ativo = true
                      AND c.ativo = true
                      AND m.usuario.id = :usuarioId
                      AND (
                           LOWER(p.nome) LIKE :termo ESCAPE '!'
                           OR LOWER(p.email) LIKE :termo ESCAPE '!'
                           OR LOWER(p.cpf) LIKE :termo ESCAPE '!'
                           OR (:termoDigitos IS NOT NULL AND p.cpf LIKE :termoDigitos ESCAPE '!')
                      )
                    """,
            countQuery = """
                    SELECT COUNT(DISTINCT p.id) FROM Consulta c
                    JOIN c.paciente p
                    JOIN c.medico m
                    WHERE p.ativo = true
                      AND c.ativo = true
                      AND m.usuario.id = :usuarioId
                      AND (
                           LOWER(p.nome) LIKE :termo ESCAPE '!'
                           OR LOWER(p.email) LIKE :termo ESCAPE '!'
                           OR LOWER(p.cpf) LIKE :termo ESCAPE '!'
                           OR (:termoDigitos IS NOT NULL AND p.cpf LIKE :termoDigitos ESCAPE '!')
                      )
                    """
    )
    Page<Paciente> findAllAtivosVinculadosAoMedicoComBusca(
            @Param("usuarioId") Long usuarioId,
            @Param("termo") String termo,
            @Param("termoDigitos") String termoDigitos,
            Pageable paginacao
    );

    @Query("""
            SELECT p FROM Consulta c
            JOIN c.paciente p
            JOIN c.medico m
            WHERE p.id = :pacienteId
              AND p.ativo = true
              AND c.ativo = true
              AND m.usuario.id = :usuarioId
            """)
    Optional<Paciente> findAtivoVinculadoAoMedico(
            @Param("pacienteId") Long pacienteId,
            @Param("usuarioId") Long usuarioId);
}
