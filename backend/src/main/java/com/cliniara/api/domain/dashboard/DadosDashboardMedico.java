package com.cliniara.api.domain.dashboard;

import com.cliniara.api.domain.consulta.DadosListagemConsulta;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

public record DadosDashboardMedico(
        @Schema(description = "Data de referência da agenda") LocalDate dataReferencia,
        @Schema(description = "Total de consultas ativas do médico hoje") long consultasHoje,
        @Schema(description = "Consultas de urgência do médico hoje") long urgenciasHoje,
        @Schema(description = "Pacientes com check-in na fila de espera do médico hoje") long filaEsperaHoje,
        @Schema(description = "Consultas do médico em atendimento agora") long emAtendimentoAgora,
        @Schema(description = "Consultas do médico concluídas hoje") long concluidasHoje,
        @Schema(description = "Consultas ativas agendadas para os próximos 7 dias") long proximosSeteDias,
        @Schema(description = "Total de pacientes ativos vinculados ao médico") long totalPacientesVinculados,
        @Schema(description = "Total de consultas ativas do médico") long totalConsultasAtivas,
        @Schema(description = "Total de prontuários ativos emitidos pelo médico") long totalProntuarios,
        @Schema(description = "Agenda ativa do médico para hoje ordenada por horário") List<DadosListagemConsulta> agendaHoje
) {
}
