package com.cliniara.api.domain.consulta;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HistoricoStatusConsultaRepository extends JpaRepository<HistoricoStatusConsulta, Long> {

    List<HistoricoStatusConsulta> findAllByConsultaIdOrderByAlteradoEmAscIdAsc(Long consultaId);
}
