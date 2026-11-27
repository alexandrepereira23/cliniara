package com.cliniara.api.domain.dashboard;

import com.cliniara.api.domain.consulta.StatusConsulta;
import io.swagger.v3.oas.annotations.media.Schema;

public record ContagemStatusDashboard(
        @Schema(description = "Status da consulta") StatusConsulta status,
        @Schema(description = "Quantidade de consultas neste status") long quantidade
) {
}
