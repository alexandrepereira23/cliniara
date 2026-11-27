package com.cliniara.api.domain.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;

public record ProducaoEspecialidadeDashboard(
        @Schema(description = "Nome da especialidade") String especialidade,
        @Schema(description = "Total de consultas no período") long total,
        @Schema(description = "Consultas concluídas no período") long concluidas,
        @Schema(description = "Consultas canceladas no período") long canceladas,
        @Schema(description = "Ausências (no-show) no período") long ausentes
) {
}
