package com.cliniara.api.domain.consulta;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

public record DadosHistoricoStatusConsulta(
        @Schema(description = "ID do registro de histórico") Long id,
        @Schema(description = "Status anterior da consulta (nulo na criação)") StatusConsulta statusAnterior,
        @Schema(description = "Novo status aplicado à consulta") StatusConsulta statusNovo,
        @Schema(description = "Login do usuário responsável pela alteração") String usuarioLogin,
        @Schema(description = "Motivo ou observação registrada") String motivo,
        @Schema(description = "Data e hora da alteração") LocalDateTime alteradoEm
) {
    public DadosHistoricoStatusConsulta(HistoricoStatusConsulta historico) {
        this(
                historico.getId(),
                historico.getStatusAnterior(),
                historico.getStatusNovo(),
                historico.getUsuarioLogin(),
                historico.getMotivo(),
                historico.getAlteradoEm()
        );
    }
}
