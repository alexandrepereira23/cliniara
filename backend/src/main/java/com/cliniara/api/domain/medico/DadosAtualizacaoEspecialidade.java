package com.cliniara.api.domain.medico;

import jakarta.validation.constraints.NotBlank;

public record DadosAtualizacaoEspecialidade(@NotBlank String nome) {
}
