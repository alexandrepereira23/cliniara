package com.cliniara.api.service;

import com.cliniara.api.domain.consulta.*;
import com.cliniara.api.domain.medico.MedicoRepository;
import com.cliniara.api.domain.usuario.Perfil;
import com.cliniara.api.domain.usuario.Usuario;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Objects;

@Service
public class ConsultaService {

    private final AgendaDeConsultas agendaDeConsultas;
    private final ConsultaRepository consultaRepository;
    private final HistoricoStatusConsultaRepository historicoStatusRepository;
    private final MedicoRepository medicoRepository;

    public ConsultaService(
            AgendaDeConsultas agendaDeConsultas,
            ConsultaRepository consultaRepository,
            HistoricoStatusConsultaRepository historicoStatusRepository,
            MedicoRepository medicoRepository
    ) {
        this.agendaDeConsultas = agendaDeConsultas;
        this.consultaRepository = consultaRepository;
        this.historicoStatusRepository = historicoStatusRepository;
        this.medicoRepository = medicoRepository;
    }

    @Transactional
    public DadosDetalhamentoConsulta agendar(DadosAgendamentoConsulta dados, Usuario usuario) {
        return agendaDeConsultas.agendar(dados, usuario);
    }

    @Transactional
    public void cancelar(DadosCancelamentoConsulta dados, Usuario usuario) {
        agendaDeConsultas.cancelar(dados, usuario);
    }

    @Transactional
    public DadosDetalhamentoConsulta confirmar(Long id, Usuario usuario) {
        return agendaDeConsultas.confirmar(id, usuario);
    }

    @Transactional
    public DadosDetalhamentoConsulta registrarCheckIn(Long id, Usuario usuario) {
        return agendaDeConsultas.registrarCheckIn(id, usuario);
    }

    @Transactional
    public DadosDetalhamentoConsulta iniciarAtendimento(Long id, Usuario usuario) {
        return agendaDeConsultas.iniciarAtendimento(id, usuario);
    }

    @Transactional
    public DadosDetalhamentoConsulta concluirAtendimento(Long id, Usuario usuario) {
        return agendaDeConsultas.concluirAtendimento(id, usuario);
    }

    @Transactional
    public DadosDetalhamentoConsulta registrarAusencia(Long id, DadosAusenciaConsulta dados, Usuario usuario) {
        return agendaDeConsultas.registrarAusencia(id, dados, usuario);
    }

    @Transactional
    public DadosDetalhamentoConsulta reagendar(Long id, DadosReagendamentoConsulta dados, Usuario usuario) {
        return agendaDeConsultas.reagendar(id, dados, usuario);
    }

    @Transactional(readOnly = true)
    public Page<DadosListagemConsulta> listar(
            Pageable paginacao,
            Usuario usuario,
            StatusConsulta status,
            boolean incluirEncerradas
    ) {
        boolean isMedico = usuario.getAuthorities().contains(Perfil.ROLE_MEDICO);
        if (status == null && !incluirEncerradas) {
            if (isMedico) {
                return consultaRepository.findAllByAtivoTrueAndMedicoUsuarioId(paginacao, usuario.getId())
                        .map(DadosListagemConsulta::new);
            }
            return consultaRepository.findAllByAtivoTrue(paginacao).map(DadosListagemConsulta::new);
        }

        if (isMedico) {
            return consultaRepository
                    .findAllByMedicoUsuarioIdWithFilters(usuario.getId(), status, incluirEncerradas, paginacao)
                    .map(DadosListagemConsulta::new);
        }
        return consultaRepository
                .findAllWithFilters(status, incluirEncerradas, paginacao)
                .map(DadosListagemConsulta::new);
    }

    @Transactional(readOnly = true)
    public List<DadosHistoricoStatusConsulta> listarHistorico(Long consultaId, Usuario usuario) {
        var consulta = consultaRepository.findById(consultaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Consulta não encontrada."));

        if (usuario.getAuthorities().contains(Perfil.ROLE_MEDICO)) {
            var medico = medicoRepository.findByUsuario(usuario)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Usuário não possui cadastro de médico."));
            if (!Objects.equals(consulta.getMedico().getId(), medico.getId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acesso negado ao histórico desta consulta.");
            }
        }

        return historicoStatusRepository.findAllByConsultaIdOrderByAlteradoEmAscIdAsc(consultaId)
                .stream()
                .map(DadosHistoricoStatusConsulta::new)
                .toList();
    }
}
