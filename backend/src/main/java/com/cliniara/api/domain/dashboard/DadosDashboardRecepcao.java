package com.cliniara.api.domain.dashboard;

import com.cliniara.api.domain.consulta.DadosListagemConsulta;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

public record DadosDashboardRecepcao(
        @Schema(description = "Data de referência da agenda") LocalDate dataReferencia,
        @Schema(description = "Total de consultas ativas no dia") long consultasHoje,
        @Schema(description = "Consultas aguardando confirmação (AGENDADA)") long aguardandoConfirmacaoHoje,
        @Schema(description = "Consultas confirmadas (CONFIRMADA)") long confirmadasHoje,
        @Schema(description = "Pacientes com check-in aguardando médico (CHECK_IN)") long aguardandoAtendimentoHoje,
        @Schema(description = "Consultas em atendimento no momento (EM_ATENDIMENTO)") long emAtendimentoHoje,
        @Schema(description = "Consultas concluídas no dia (CONCLUIDA)") long concluidasHoje,
        @Schema(description = "Consultas canceladas no dia (CANCELADA)") long canceladasHoje,
        @Schema(description = "Pacientes ausentes no dia (AUSENTE)") long ausentesHoje,
        @Schema(description = "Próxima consulta ativa pendente do dia") DadosListagemConsulta proximaConsulta,
        @Schema(description = "Agenda ativa do dia ordenada por horário") List<DadosListagemConsulta> agendaHoje
) {
}
