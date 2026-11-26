package com.cliniara.api.domain.medico;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import com.cliniara.api.domain.endereco.DadosEndereco;

public record DadosAtualizacaoMedico(
        @NotNull
        @Schema(example = "1")
        Long id,
        String nome,
        String telefone,
        DadosEndereco endereco) {

}
