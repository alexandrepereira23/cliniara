package com.cliniara.api.domain.consulta;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record DadosReagendamentoConsulta(

        @NotNull
        @Future
        @Schema(example = "2026-12-10T10:00:00", description = "Nova data e hora da consulta")
        LocalDateTime novaData,

        @Schema(example = "1", description = "ID do novo médico (opcional; mantém o atual se omitido)")
        Long idMedico,

        @NotNull
        @Schema(example = "CLINICA", description = "Quem solicitou o reagendamento: PACIENTE (exige 24h) ou CLINICA")
        CanceladoPor solicitadoPor,

        @Size(max = 255)
        @Schema(example = "Remanejamento de agenda médica", description = "Motivo ou justificativa do reagendamento (opcional)")
        String motivo
) {
}
