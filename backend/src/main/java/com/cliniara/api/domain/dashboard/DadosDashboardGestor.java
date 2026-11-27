package com.cliniara.api.domain.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record DadosDashboardGestor(
        @Schema(description = "Quantidade de dias considerados no recorte") int periodoDias,
        @Schema(description = "Total de consultas ativas hoje") long consultasHoje,
        @Schema(description = "Total de consultas no período (todos os status)") long totalConsultasPeriodo,
        @Schema(description = "Consultas concluídas no período") long concluidasPeriodo,
        @Schema(description = "Consultas canceladas no período") long canceladasPeriodo,
        @Schema(description = "Ausências registradas no período") long ausentesPeriodo,
        @Schema(description = "Consultas reagendadas no período") long reagendadasPeriodo,
        @Schema(description = "Percentual de cancelamentos sobre o total do período") double taxaCancelamentoPercentual,
        @Schema(description = "Percentual de ausências sobre o total do período") double taxaAbsenteismoPercentual,
        @Schema(description = "Total de médicos ativos na clínica") long medicosAtivos,
        @Schema(description = "Total de pacientes ativos na clínica") long pacientesAtivos,
        @Schema(description = "Distribuição de consultas por status no período") List<ContagemStatusDashboard> porStatus,
        @Schema(description = "Produção agregada por especialidade no período") List<ProducaoEspecialidadeDashboard> porEspecialidade
) {
}
