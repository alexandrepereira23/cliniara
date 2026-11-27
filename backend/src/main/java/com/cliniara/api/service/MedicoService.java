package com.cliniara.api.service;

import com.cliniara.api.domain.consulta.ConsultaRepository;
import com.cliniara.api.domain.medico.*;
import com.cliniara.api.domain.usuario.Perfil;
import com.cliniara.api.domain.usuario.Usuario;
import com.cliniara.api.exception.ConflitoException;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class MedicoService {

    private final MedicoRepository medicoRepository;
    private final EspecialidadeRepository especialidadeRepository;
    private final ConsultaRepository consultaRepository;

    public MedicoService(
            MedicoRepository medicoRepository,
            EspecialidadeRepository especialidadeRepository,
            ConsultaRepository consultaRepository
    ) {
        this.medicoRepository = medicoRepository;
        this.especialidadeRepository = especialidadeRepository;
        this.consultaRepository = consultaRepository;
    }

    @Transactional
    public DadosDetalhamentoMedico cadastrar(DadosCadastroMedico dados) {
        var especialidade = especialidadeRepository.getReferenceById(dados.especialidadeId());
        var medico = new Medico(dados, especialidade);
        medicoRepository.save(medico);
        return new DadosDetalhamentoMedico(medico);
    }

    @Transactional
    public DadosDetalhamentoMedico atualizar(DadosAtualizacaoMedico dados) {
        var medico = medicoRepository.getReferenceById(dados.id());
        medico.atualizarInformacoes(dados);
        return new DadosDetalhamentoMedico(medico);
    }

    @Transactional
    public void excluir(Long id) {
        var medico = medicoRepository.findById(id)
                .orElseThrow(EntityNotFoundException::new);

        long consultasPendentes = consultaRepository.countConsultasPendentesOuFuturasDoMedico(id, LocalDateTime.now());
        if (consultasPendentes > 0) {
            throw new ConflitoException(
                    "Não é possível inativar o médico pois existem " + consultasPendentes
                            + " consulta(s) futura(s) ou em atendimento vinculada(s) a ele. "
                            + "Cancele ou reagende as consultas antes de inativar.");
        }

        medico.excluir();
    }

    @Transactional(readOnly = true)
    public DadosDetalhamentoMedico detalhar(Long id) {
        var medico = medicoRepository.getReferenceById(id);
        return new DadosDetalhamentoMedico(medico);
    }

    @Transactional(readOnly = true)
    public Page<DadosListagemMedico> listar(Pageable paginacao, Usuario usuario) {
        return listar(paginacao, usuario, null, null);
    }

    @Transactional(readOnly = true)
    public Page<DadosListagemMedico> listar(Pageable paginacao, Usuario usuario, String q, Long especialidadeId) {
        String termo = BuscaUtil.paraLikeContendo(q);
        boolean isMedico = usuario.getAuthorities().contains(Perfil.ROLE_MEDICO);

        if (termo == null && especialidadeId == null) {
            if (isMedico) {
                return medicoRepository.findAllByAtivoTrueAndUsuarioId(paginacao, usuario.getId())
                        .map(DadosListagemMedico::new);
            }
            return medicoRepository.findAllByAtivoTrue(paginacao).map(DadosListagemMedico::new);
        }

        if (isMedico) {
            return medicoRepository.findAllAtivosByUsuarioIdComFiltros(usuario.getId(), termo, especialidadeId, paginacao)
                    .map(DadosListagemMedico::new);
        }
        return medicoRepository.findAllAtivosComFiltros(termo, especialidadeId, paginacao)
                .map(DadosListagemMedico::new);
    }
}
