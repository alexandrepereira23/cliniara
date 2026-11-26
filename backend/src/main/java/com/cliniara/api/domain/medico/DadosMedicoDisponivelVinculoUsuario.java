package com.cliniara.api.domain.medico;

public record DadosMedicoDisponivelVinculoUsuario(Long id, String nome, String crm) {

    public DadosMedicoDisponivelVinculoUsuario(Medico medico) {
        this(medico.getId(), medico.getNome(), medico.getCrm());
    }
}
