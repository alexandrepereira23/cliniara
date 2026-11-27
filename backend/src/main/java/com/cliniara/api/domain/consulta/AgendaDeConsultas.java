package com.cliniara.api.domain.consulta;

import com.cliniara.api.domain.convenio.Convenio;
import com.cliniara.api.domain.convenio.ConvenioRepository;
import com.cliniara.api.domain.convenio.TipoConvenio;
import com.cliniara.api.domain.medico.DisponibilidadeMedicoRepository;
import com.cliniara.api.domain.medico.Medico;
import com.cliniara.api.domain.medico.MedicoConvenioRepository;
import com.cliniara.api.domain.medico.MedicoRepository;
import com.cliniara.api.domain.paciente.ConvenioPaciente;
import com.cliniara.api.domain.paciente.ConvenioPacienteRepository;
import com.cliniara.api.domain.paciente.PacienteRepository;
import com.cliniara.api.domain.usuario.Usuario;
import com.cliniara.api.exception.ValidacaoException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

@Service
public class AgendaDeConsultas {

    private final ConsultaRepository consultaRepository;
    private final MedicoRepository medicoRepository;
    private final PacienteRepository pacienteRepository;
    private final DisponibilidadeMedicoRepository disponibilidadeRepository;
    private final ConvenioRepository convenioRepository;
    private final MedicoConvenioRepository medicoConvenioRepository;
    private final ConvenioPacienteRepository convenioPacienteRepository;
    private final HistoricoStatusConsultaRepository historicoStatusRepository;

    public AgendaDeConsultas(ConsultaRepository consultaRepository,
                             MedicoRepository medicoRepository,
                             PacienteRepository pacienteRepository,
                             DisponibilidadeMedicoRepository disponibilidadeRepository,
                             ConvenioRepository convenioRepository,
                             MedicoConvenioRepository medicoConvenioRepository,
                             ConvenioPacienteRepository convenioPacienteRepository,
                             HistoricoStatusConsultaRepository historicoStatusRepository) {
        this.consultaRepository = consultaRepository;
        this.medicoRepository = medicoRepository;
        this.pacienteRepository = pacienteRepository;
        this.disponibilidadeRepository = disponibilidadeRepository;
        this.convenioRepository = convenioRepository;
        this.medicoConvenioRepository = medicoConvenioRepository;
        this.convenioPacienteRepository = convenioPacienteRepository;
        this.historicoStatusRepository = historicoStatusRepository;
    }

    public DadosDetalhamentoConsulta agendar(DadosAgendamentoConsulta dados) {
        return agendar(dados, null);
    }

    public DadosDetalhamentoConsulta agendar(DadosAgendamentoConsulta dados, Usuario usuario) {

        // 1. Validações de Pré-Requisitos (Inatividade, Existência)
        var paciente = pacienteRepository.findById(dados.idPaciente());
        if (paciente.isEmpty() || !paciente.get().isAtivo()) {
            throw new ValidacaoException("Paciente não existe ou está inativo no sistema!");
        }

        Medico medico = null;
        if (dados.idMedico() != null) {
            var medicoOptional = medicoRepository.findById(dados.idMedico());
            if (medicoOptional.isEmpty() || !medicoOptional.get().isAtivo()) {
                throw new ValidacaoException("Médico não existe ou está inativo no sistema!");
            }
            medico = medicoOptional.get();
        }

        // 2. Validações de Regras de Negócio
        var prioridade = dados.prioridade() != null ? dados.prioridade() : PrioridadeConsulta.ROTINA;
        validarHorarioFuncionamento(dados.data());
        validarAntecedenciaMinima(dados.data(), prioridade);

        Consulta consultaOrigem = null;
        if (dados.consultaOrigemId() != null) {
            consultaOrigem = validarRetorno(dados.consultaOrigemId(), dados.idPaciente(), dados.idMedico(), dados.data());
            if (medico == null) {
                var medicoOrigem = consultaOrigem.getMedico();
                if (medicoOrigem == null || !medicoOrigem.isAtivo()) {
                    throw new ValidacaoException("O médico da consulta original está inativo no sistema!");
                }
                medico = medicoOrigem;
            }
        } else {
            validarPacienteComOutraConsultaNoDia(dados.idPaciente(), dados.data());
        }

        // Escolhe o médico (se não foi informado e não é retorno, será aleatório)
        if (medico == null) {
            medico = escolherMedicoAleatorio(dados.data());
        } else {
            validarDisponibilidadeMedico(medico.getId(), dados.data());
            validarMedicoOcupado(medico.getId(), dados.data());
        }

        if (medico == null) {
            throw new ValidacaoException("Não existe médico disponível na data e horário informados!");
        }

        // 3. Persistência e elegibilidade de convênio
        var consulta = consultaOrigem != null
                ? new Consulta(medico, paciente.get(), dados.data(), prioridade, consultaOrigem)
                : new Consulta(medico, paciente.get(), dados.data(), prioridade);

        if (dados.convenioId() != null) {
            Convenio convenio = validarConvenio(dados.convenioId(), medico.getId(), dados.idPaciente(), dados.data());
            consulta.setConvenio(convenio);
        }

        consultaRepository.save(consulta);

        String motivoInicial = consultaOrigem != null
                ? "Retorno agendado a partir da consulta #" + consultaOrigem.getId()
                : "Consulta agendada";
        registrarHistorico(consulta, null, StatusConsulta.AGENDADA, usuario, motivoInicial);

        // 4. Retorno
        return new DadosDetalhamentoConsulta(consulta);
    }

    // ------------------ TRANSIÇÕES DO CICLO DA CONSULTA ------------------

    public DadosDetalhamentoConsulta confirmar(Long idConsulta, Usuario usuario) {
        var consulta = buscarConsultaOuErro(idConsulta);
        var statusAnterior = consulta.getStatus();
        consulta.confirmar();
        registrarHistorico(consulta, statusAnterior, consulta.getStatus(), usuario, "Consulta confirmada pela recepção");
        return new DadosDetalhamentoConsulta(consulta);
    }

    public DadosDetalhamentoConsulta registrarCheckIn(Long idConsulta, Usuario usuario) {
        var consulta = buscarConsultaOuErro(idConsulta);
        var statusAnterior = consulta.getStatus();
        consulta.registrarCheckIn();
        registrarHistorico(consulta, statusAnterior, consulta.getStatus(), usuario, "Check-in realizado na recepção");
        return new DadosDetalhamentoConsulta(consulta);
    }

    public DadosDetalhamentoConsulta iniciarAtendimento(Long idConsulta, Usuario usuario) {
        var consulta = buscarConsultaOuErro(idConsulta);
        validarMedicoResponsavel(consulta, usuario);
        var statusAnterior = consulta.getStatus();
        consulta.iniciarAtendimento();
        registrarHistorico(consulta, statusAnterior, consulta.getStatus(), usuario, "Atendimento iniciado pelo médico");
        return new DadosDetalhamentoConsulta(consulta);
    }

    public DadosDetalhamentoConsulta concluirAtendimento(Long idConsulta, Usuario usuario) {
        var consulta = buscarConsultaOuErro(idConsulta);
        validarMedicoResponsavel(consulta, usuario);
        var statusAnterior = consulta.getStatus();
        consulta.concluirAtendimento();
        registrarHistorico(consulta, statusAnterior, consulta.getStatus(), usuario, "Atendimento concluído pelo médico");
        return new DadosDetalhamentoConsulta(consulta);
    }

    public DadosDetalhamentoConsulta registrarAusencia(Long idConsulta, DadosAusenciaConsulta dados, Usuario usuario) {
        var consulta = buscarConsultaOuErro(idConsulta);
        if (consulta.getDataHora().toLocalDate().isAfter(LocalDate.now())) {
            throw new ValidacaoException("Ausência só pode ser registrada na data da consulta ou após o horário agendado.");
        }
        var statusAnterior = consulta.getStatus();
        consulta.registrarAusencia();
        String motivo = (dados != null && dados.observacao() != null && !dados.observacao().isBlank())
                ? dados.observacao().trim()
                : "Paciente ausente";
        registrarHistorico(consulta, statusAnterior, consulta.getStatus(), usuario, motivo);
        return new DadosDetalhamentoConsulta(consulta);
    }

    public DadosDetalhamentoConsulta reagendar(Long idConsulta, DadosReagendamentoConsulta dados, Usuario usuario) {
        var consultaAtual = buscarConsultaOuErro(idConsulta);

        if (!consultaAtual.getStatus().permiteCancelamentoOuReagendamento()) {
            throw new ValidacaoException("A consulta no status " + consultaAtual.getStatus() + " não pode ser reagendada.");
        }

        if (dados.solicitadoPor() == CanceladoPor.PACIENTE
                && consultaAtual.getDataHora().isBefore(LocalDateTime.now().plusHours(24))) {
            throw new ValidacaoException("Reagendamento solicitado pelo paciente exige antecedência mínima de 24 horas.");
        }

        var paciente = consultaAtual.getPaciente();
        if (paciente == null || !paciente.isAtivo()) {
            throw new ValidacaoException("Paciente não existe ou está inativo no sistema!");
        }

        Long medicoDestinoId = dados.idMedico() != null ? dados.idMedico() : consultaAtual.getMedico().getId();
        var medicoDestino = medicoRepository.findById(medicoDestinoId)
                .filter(Medico::isAtivo)
                .orElseThrow(() -> new ValidacaoException("Médico não existe ou está inativo no sistema!"));

        if (Objects.equals(medicoDestino.getId(), consultaAtual.getMedico().getId())
                && Objects.equals(dados.novaData(), consultaAtual.getDataHora())) {
            throw new ValidacaoException("A nova data/horário ou médico deve ser diferente do agendamento atual.");
        }

        var prioridade = consultaAtual.getPrioridade() != null ? consultaAtual.getPrioridade() : PrioridadeConsulta.ROTINA;
        validarHorarioFuncionamento(dados.novaData());
        validarAntecedenciaMinima(dados.novaData(), prioridade);

        var statusAnterior = consultaAtual.getStatus();
        consultaAtual.marcarComoReagendada();
        consultaRepository.saveAndFlush(consultaAtual);

        Consulta consultaOrigem = consultaAtual.getConsultaOrigem();
        if (consultaOrigem != null) {
            validarRetorno(consultaOrigem.getId(), paciente.getId(), medicoDestino.getId(), dados.novaData());
        } else {
            validarPacienteComOutraConsultaNoDia(paciente.getId(), dados.novaData());
        }

        validarDisponibilidadeMedico(medicoDestino.getId(), dados.novaData());
        validarMedicoOcupado(medicoDestino.getId(), dados.novaData());

        var novaConsulta = consultaOrigem != null
                ? new Consulta(medicoDestino, paciente, dados.novaData(), prioridade, consultaOrigem)
                : new Consulta(medicoDestino, paciente, dados.novaData(), prioridade);
        novaConsulta.setConsultaReagendadaDe(consultaAtual);

        if (consultaAtual.getConvenio() != null) {
            Convenio convenioValidado = validarConvenio(
                    consultaAtual.getConvenio().getId(),
                    medicoDestino.getId(),
                    paciente.getId(),
                    dados.novaData()
            );
            novaConsulta.setConvenio(convenioValidado);
        }

        consultaRepository.save(novaConsulta);

        String motivoBase = (dados.motivo() != null && !dados.motivo().isBlank())
                ? dados.motivo().trim()
                : "Reagendamento solicitado por " + dados.solicitadoPor().name();
        String motivoOriginal = novaConsulta.getId() != null
                ? motivoBase + " (nova consulta #" + novaConsulta.getId() + ")"
                : motivoBase;
        registrarHistorico(consultaAtual, statusAnterior, StatusConsulta.REAGENDADA, usuario, motivoOriginal);

        String motivoNova = consultaAtual.getId() != null
                ? "Reagendada a partir da consulta #" + consultaAtual.getId() + " — " + motivoBase
                : "Reagendada — " + motivoBase;
        registrarHistorico(novaConsulta, null, StatusConsulta.AGENDADA, usuario, motivoNova);

        return new DadosDetalhamentoConsulta(novaConsulta);
    }

    // ------------------ MÉTODO DE CANCELAMENTO ------------------

    public void cancelar(DadosCancelamentoConsulta dados) {
        cancelar(dados, null);
    }

    public void cancelar(DadosCancelamentoConsulta dados, Usuario usuario) {
        var consulta = consultaRepository.findById(dados.idConsulta())
                .orElseThrow(() -> new ValidacaoException("ID da consulta informado não existe!"));

        if (!consulta.getStatus().permiteCancelamentoOuReagendamento()) {
            throw new ValidacaoException("A consulta no status " + consulta.getStatus() + " não pode ser cancelada.");
        }

        if (dados.motivo() == MotivoCancelamento.MEDICO_CANCELOU && dados.canceladoPor() == CanceladoPor.PACIENTE) {
            throw new ValidacaoException("Cancelamento por indisponibilidade médica não pode ser atribuído ao paciente.");
        }

        CanceladoPor canceladoPorEfetivo = resolverCanceladoPor(dados);
        if (canceladoPorEfetivo == CanceladoPor.PACIENTE
                && consulta.getDataHora().isBefore(LocalDateTime.now().plusHours(24))) {
            throw new ValidacaoException("Consulta somente pode ser cancelada pelo paciente com antecedência mínima de 24 horas.");
        }

        var statusAnterior = consulta.getStatus();
        consulta.cancelar(dados.motivo(), canceladoPorEfetivo);
        registrarHistorico(
                consulta,
                statusAnterior,
                StatusConsulta.CANCELADA,
                usuario,
                dados.motivo().name() + " (" + canceladoPorEfetivo.name() + ")"
        );
    }

    // ------------------ MÉTODOS DE VALIDAÇÃO DE REGRA DE NEGÓCIO ------------------

    private Consulta buscarConsultaOuErro(Long idConsulta) {
        return consultaRepository.findById(idConsulta)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Consulta não encontrada."));
    }

    private void validarMedicoResponsavel(Consulta consulta, Usuario usuario) {
        if (usuario == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Usuário médico não identificado.");
        }
        var medicoLogado = medicoRepository.findByUsuario(usuario)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Usuário não possui cadastro de médico."));
        if (!Objects.equals(consulta.getMedico().getId(), medicoLogado.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Apenas o médico responsável pela consulta pode executar esta ação.");
        }
    }

    private CanceladoPor resolverCanceladoPor(DadosCancelamentoConsulta dados) {
        if (dados.motivo() == MotivoCancelamento.MEDICO_CANCELOU) {
            return CanceladoPor.CLINICA;
        }
        if (dados.canceladoPor() != null) {
            return dados.canceladoPor();
        }
        return CanceladoPor.PACIENTE;
    }

    private void registrarHistorico(
            Consulta consulta,
            StatusConsulta statusAnterior,
            StatusConsulta statusNovo,
            Usuario usuario,
            String motivo
    ) {
        if (historicoStatusRepository != null) {
            historicoStatusRepository.save(new HistoricoStatusConsulta(consulta, statusAnterior, statusNovo, usuario, motivo));
        }
    }

    private Convenio validarConvenio(Long convenioId, Long medicoId, Long pacienteId, LocalDateTime dataConsulta) {
        Convenio convenio = convenioRepository.findById(convenioId)
                .orElseThrow(() -> new ValidacaoException("Convênio informado não encontrado."));
        if (!convenio.isAtivo()) {
            throw new ValidacaoException("Convênio informado está inativo.");
        }
        if (!medicoConvenioRepository.existsByMedicoIdAndConvenioIdAndAtivoTrue(medicoId, convenioId)) {
            throw new ValidacaoException("Médico não aceita o convênio informado.");
        }
        if (convenio.getTipo() == TipoConvenio.PLANO) {
            var vinculoPaciente = convenioPacienteRepository
                    .findByPacienteIdAndConvenioId(pacienteId, convenioId)
                    .filter(ConvenioPaciente::isAtivo)
                    .orElseThrow(() -> new ValidacaoException("Paciente não possui vínculo ativo com o convênio informado."));
            if (vinculoPaciente.getValidade() != null
                    && vinculoPaciente.getValidade().isBefore(dataConsulta.toLocalDate())) {
                throw new ValidacaoException("A carteirinha do paciente neste convênio está vencida para a data da consulta.");
            }
        }
        return convenio;
    }

    private void validarHorarioFuncionamento(LocalDateTime data) {
        var diaDaSemana = data.getDayOfWeek();
        var hora = data.getHour();

        if (diaDaSemana == DayOfWeek.SUNDAY) {
            throw new ValidacaoException("Clínica não funciona aos domingos.");
        }

        if (hora < 7 || hora >= 19) {
            throw new ValidacaoException("Horário de agendamento fora do funcionamento da clínica.");
        }
    }

    private void validarAntecedenciaMinima(LocalDateTime data, PrioridadeConsulta prioridade) {
        if (prioridade == PrioridadeConsulta.URGENCIA) {
            return;
        }
        var agora = LocalDateTime.now();
        int minutosMinimos = prioridade == PrioridadeConsulta.PRIORITARIO ? 10 : 30;
        if (data.isBefore(agora.plusMinutes(minutosMinimos))) {
            throw new ValidacaoException(
                    "Consulta deve ser agendada com antecedência mínima de " + minutosMinimos + " minutos.");
        }
    }

    private void validarPacienteComOutraConsultaNoDia(Long idPaciente, LocalDateTime data) {
        var primeiroHorario = data.withHour(7).withMinute(0);
        var ultimoHorario = data.withHour(19).withMinute(0);

        if (consultaRepository.existsByPacienteIdAndDataHoraBetweenAndAtivoTrue(idPaciente, primeiroHorario, ultimoHorario)) {
            throw new ValidacaoException("Paciente já possui uma consulta agendada neste dia.");
        }
    }

    private void validarMedicoOcupado(Long idMedico, LocalDateTime dataHora) {
        if (consultaRepository.existsByMedicoIdAndDataHoraAndAtivoTrue(idMedico, dataHora)) {
            throw new ValidacaoException("Médico já possui outra consulta ATIVA agendada neste horário.");
        }
    }

    private void validarDisponibilidadeMedico(long medicoId, LocalDateTime dataHora) {
        var diaSemana = dataHora.getDayOfWeek();
        var hora = dataHora.toLocalTime();
        boolean temDisponibilidade = disponibilidadeRepository
                .existsByMedicoIdAndDiaSemanaAndHoraInicioLessThanEqualAndHoraFimGreaterThanAndAtivoTrue(
                        medicoId, diaSemana, hora, hora);
        if (!temDisponibilidade) {
            throw new ValidacaoException("Médico não possui disponibilidade cadastrada no dia/horário solicitado.");
        }
    }

    private Consulta validarRetorno(Long consultaOrigemId, Long idPaciente, Long idMedicoInformado, LocalDateTime dataRetorno) {
        var origem = consultaRepository.findById(consultaOrigemId)
                .orElseThrow(() -> new ValidacaoException("Consulta de origem não encontrada!"));

        if (!origem.isAtivo() || origem.getStatus() != StatusConsulta.CONCLUIDA) {
            throw new ValidacaoException("O retorno só pode ser agendado para uma consulta já concluída.");
        }

        if (!Objects.equals(origem.getPaciente().getId(), idPaciente)) {
            throw new ValidacaoException("O retorno deve pertencer ao mesmo paciente da consulta original.");
        }

        if (!dataRetorno.isAfter(origem.getDataHora())) {
            throw new ValidacaoException("O retorno deve ocorrer após a data e horário da consulta original.");
        }

        if (dataRetorno.isAfter(origem.getDataHora().plusDays(30))) {
            throw new ValidacaoException("Retorno deve ocorrer em até 30 dias após a consulta original.");
        }

        if (consultaRepository.existsByConsultaOrigemIdAndAtivoTrue(consultaOrigemId)) {
            throw new ValidacaoException("Já existe um retorno ativo agendado para esta consulta.");
        }

        if (idMedicoInformado != null && !Objects.equals(idMedicoInformado, origem.getMedico().getId())) {
            throw new ValidacaoException("O retorno deve ser agendado com o mesmo médico da consulta original.");
        }

        return origem;
    }

    private Medico escolherMedicoAleatorio(LocalDateTime data) {
        var diaSemana = data.getDayOfWeek().name();
        var hora = data.toLocalTime();
        Optional<Medico> medicoOptional = medicoRepository.escolherMedicoAleatorioLivreNaData(
                data, diaSemana, hora);
        return medicoOptional.orElse(null);
    }
}
