package com.cliniara.api.domain.medico;

import com.cliniara.api.domain.convenio.TipoConvenio;

public record DadosDetalhamentoConvenioMedico(
        Long id,
        Long convenioId,
        String nomeConvenio,
        String codigoANS,
        TipoConvenio tipo
) {
    public DadosDetalhamentoConvenioMedico(MedicoConvenio mc) {
        this(mc.getId(), mc.getConvenio().getId(), mc.getConvenio().getNome(),
                mc.getConvenio().getCodigoANS(), mc.getConvenio().getTipo());
    }
}
