package com.cliniara.api.service;

import com.cliniara.api.domain.consulta.*;
import com.cliniara.api.domain.usuario.Perfil;
import com.cliniara.api.domain.usuario.Usuario;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConsultaService {

    private final AgendaDeConsultas agendaDeConsultas;
    private final ConsultaRepository consultaRepository;

    public ConsultaService(AgendaDeConsultas agendaDeConsultas, ConsultaRepository consultaRepository) {
        this.agendaDeConsultas = agendaDeConsultas;
        this.consultaRepository = consultaRepository;
    }

    @Transactional
    public DadosDetalhamentoConsulta agendar(DadosAgendamentoConsulta dados) {
        return agendaDeConsultas.agendar(dados);
    }

    @Transactional
    public void cancelar(DadosCancelamentoConsulta dados) {
        agendaDeConsultas.cancelar(dados);
    }

    @Transactional(readOnly = true)
    public Page<DadosListagemConsulta> listarAtivas(Pageable paginacao, Usuario usuario) {
        if (usuario.getAuthorities().contains(Perfil.ROLE_MEDICO)) {
            return consultaRepository.findAllByAtivoTrueAndMedicoUsuarioId(paginacao, usuario.getId())
                    .map(DadosListagemConsulta::new);
        }
        return consultaRepository.findAllByAtivoTrue(paginacao).map(DadosListagemConsulta::new);
    }
}