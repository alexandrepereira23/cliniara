package com.cliniara.api.domain.consulta;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

public record DadosListagemConsulta(
        @Schema(description = "ID da consulta") Long id,
        @Schema(description = "ID do médico") Long idMedico,
        @Schema(description = "Nome do médico") String nomeMedico,
        @Schema(description = "ID do paciente") Long idPaciente,
        @Schema(description = "Nome do paciente") String nomePaciente,
        @Schema(description = "Data e hora da consulta") LocalDateTime dataHora,
        @Schema(description = "Prioridade: ROTINA, PRIORITARIO ou URGENCIA") PrioridadeConsulta prioridade,
        @Schema(description = "Tipo: NORMAL ou RETORNO") TipoConsulta tipo,
        @Schema(description = "Status do ciclo clínico da consulta") StatusConsulta status,
        @Schema(description = "ID da consulta original quando retorno") Long consultaOrigemId,
        @Schema(description = "ID da consulta anterior quando reagendada") Long consultaReagendadaDeId
) {
    public DadosListagemConsulta(Consulta consulta) {
        this(
                consulta.getId(),
                consulta.getMedico().getId(),
                consulta.getMedico().getNome(),
                consulta.getPaciente().getId(),
                consulta.getPaciente().getNome(),
                consulta.getDataHora(),
                consulta.getPrioridade(),
                consulta.getTipo(),
                consulta.getStatus(),
                consulta.getConsultaOrigem() != null ? consulta.getConsultaOrigem().getId() : null,
                consulta.getConsultaReagendadaDe() != null ? consulta.getConsultaReagendadaDe().getId() : null
        );
    }
}
