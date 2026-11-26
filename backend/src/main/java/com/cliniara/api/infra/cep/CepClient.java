package com.cliniara.api.infra.cep;

public interface CepClient {
    DadosEnderecoExterno buscarEnderecoPorCep(String cepNormalizado);
}
