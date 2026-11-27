package com.cliniara.api.domain.medico;

import com.cliniara.api.domain.usuario.Usuario;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;

public interface MedicoRepository extends JpaRepository<Medico, Long> {

    Page<Medico> findAllByAtivoTrue(Pageable paginacao);

    long countByAtivoTrue();

    Page<Medico> findAllByAtivoTrueAndUsuarioIsNull(Pageable paginacao);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM Medico m WHERE m.id = :id")
    Optional<Medico> findByIdComBloqueio(@Param("id") Long id);

    @Query("SELECT m FROM Medico m WHERE m.ativo = true AND m.usuario.id = :usuarioId")
    Page<Medico> findAllByAtivoTrueAndUsuarioId(Pageable paginacao, @Param("usuarioId") Long usuarioId);

    @Query(
            value = """
                    SELECT m FROM Medico m
                    JOIN FETCH m.especialidade e
                    WHERE m.ativo = true
                      AND (:especialidadeId IS NULL OR e.id = :especialidadeId)
                      AND (
                           :termo IS NULL
                           OR LOWER(m.nome) LIKE :termo ESCAPE '!'
                           OR LOWER(m.crm) LIKE :termo ESCAPE '!'
                           OR LOWER(m.email) LIKE :termo ESCAPE '!'
                           OR LOWER(e.nome) LIKE :termo ESCAPE '!'
                      )
                    """,
            countQuery = """
                    SELECT COUNT(m) FROM Medico m
                    JOIN m.especialidade e
                    WHERE m.ativo = true
                      AND (:especialidadeId IS NULL OR e.id = :especialidadeId)
                      AND (
                           :termo IS NULL
                           OR LOWER(m.nome) LIKE :termo ESCAPE '!'
                           OR LOWER(m.crm) LIKE :termo ESCAPE '!'
                           OR LOWER(m.email) LIKE :termo ESCAPE '!'
                           OR LOWER(e.nome) LIKE :termo ESCAPE '!'
                      )
                    """
    )
    Page<Medico> findAllAtivosComFiltros(
            @Param("termo") String termo,
            @Param("especialidadeId") Long especialidadeId,
            Pageable paginacao
    );

    @Query(
            value = """
                    SELECT m FROM Medico m
                    JOIN FETCH m.especialidade e
                    WHERE m.ativo = true
                      AND m.usuario.id = :usuarioId
                      AND (:especialidadeId IS NULL OR e.id = :especialidadeId)
                      AND (
                           :termo IS NULL
                           OR LOWER(m.nome) LIKE :termo ESCAPE '!'
                           OR LOWER(m.crm) LIKE :termo ESCAPE '!'
                           OR LOWER(m.email) LIKE :termo ESCAPE '!'
                           OR LOWER(e.nome) LIKE :termo ESCAPE '!'
                      )
                    """,
            countQuery = """
                    SELECT COUNT(m) FROM Medico m
                    JOIN m.especialidade e
                    WHERE m.ativo = true
                      AND m.usuario.id = :usuarioId
                      AND (:especialidadeId IS NULL OR e.id = :especialidadeId)
                      AND (
                           :termo IS NULL
                           OR LOWER(m.nome) LIKE :termo ESCAPE '!'
                           OR LOWER(m.crm) LIKE :termo ESCAPE '!'
                           OR LOWER(m.email) LIKE :termo ESCAPE '!'
                           OR LOWER(e.nome) LIKE :termo ESCAPE '!'
                      )
                    """
    )
    Page<Medico> findAllAtivosByUsuarioIdComFiltros(
            @Param("usuarioId") Long usuarioId,
            @Param("termo") String termo,
            @Param("especialidadeId") Long especialidadeId,
            Pageable paginacao
    );

    Optional<Medico> findByUsuario(Usuario usuario);

    @Query(value = """
        SELECT m.* FROM medicos m
        INNER JOIN disponibilidade_medico d ON d.medico_id = m.id
            AND d.dia_semana = :diaSemana
            AND d.hora_inicio <= :hora
            AND d.hora_fim > :hora
            AND d.ativo = true
        LEFT JOIN consultas c ON c.medico_id = m.id
            AND c.data_hora = :dataHora
            AND c.ativo = true
        WHERE m.ativo = true
        AND c.id IS NULL
        ORDER BY RAND()
        LIMIT 1
    """, nativeQuery = true)
    Optional<Medico> escolherMedicoAleatorioLivreNaData(
            @Param("dataHora") LocalDateTime dataHora,
            @Param("diaSemana") String diaSemana,
            @Param("hora") LocalTime hora);
}
