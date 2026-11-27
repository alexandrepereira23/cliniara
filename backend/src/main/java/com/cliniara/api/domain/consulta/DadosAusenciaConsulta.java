package com.cliniara.api.domain.consulta;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

public record DadosAusenciaConsulta(

        @Size(max = 255)
        @Schema(example = "Paciente não compareceu após 20 minutos de tolerância", description = "Observação opcional sobre a ausência")
        String observacao
) {
}
