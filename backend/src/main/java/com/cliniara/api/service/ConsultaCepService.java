package com.cliniara.api.service;

import com.cliniara.api.domain.endereco.DadosEnderecoCep;
import com.cliniara.api.exception.CepInvalidoException;
import com.cliniara.api.exception.RespostaInvalidaCepException;
import com.cliniara.api.infra.cep.CepClient;
import org.springframework.stereotype.Service;

@Service
public class ConsultaCepService {

    private final CepClient cepClient;

    public ConsultaCepService(CepClient cepClient) {
        this.cepClient = cepClient;
    }

    public DadosEnderecoCep consultar(String cepRaw) {
        if (cepRaw == null || cepRaw.isBlank()) {
            throw new CepInvalidoException("CEP inválido. Informe 8 dígitos.");
        }

        var trimmed = cepRaw.trim();
        // Aceita exatamente 8 dígitos contínuos ou máscara 00000-000
        if (!trimmed.matches("^\\d{8}$") && !trimmed.matches("^\\d{5}-\\d{3}$")) {
            throw new CepInvalidoException("CEP inválido. Informe 8 dígitos.");
        }

        var cepNormalizado = trimmed.replace("-", "");
        var dadosExternos = cepClient.buscarEnderecoPorCep(cepNormalizado);

        if (dadosExternos == null || dadosExternos.cidade() == null || dadosExternos.uf() == null) {
            throw new RespostaInvalidaCepException("Resposta inválida do serviço de consulta de CEP.");
        }

        // Formata o CEP para exibição padronizada 00000-000
        var cepFormatado = cepNormalizado.substring(0, 5) + "-" + cepNormalizado.substring(5);
        var logradouro = dadosExternos.logradouro() != null ? dadosExternos.logradouro() : "";
        var bairro = dadosExternos.bairro() != null ? dadosExternos.bairro() : "";
        var complemento = dadosExternos.complemento() != null ? dadosExternos.complemento() : "";

        return new DadosEnderecoCep(
                cepFormatado,
                logradouro,
                bairro,
                dadosExternos.cidade(),
                dadosExternos.uf(),
                complemento
        );
    }
}
