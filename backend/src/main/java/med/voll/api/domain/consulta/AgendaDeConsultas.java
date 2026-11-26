package med.voll.api.domain.consulta;

import med.voll.api.domain.convenio.Convenio;
import med.voll.api.domain.convenio.ConvenioRepository;
import med.voll.api.domain.convenio.TipoConvenio;
import med.voll.api.domain.medico.DisponibilidadeMedicoRepository;
import med.voll.api.domain.medico.Medico;
import med.voll.api.domain.medico.MedicoConvenioRepository;
import med.voll.api.domain.medico.MedicoRepository;
import med.voll.api.domain.paciente.ConvenioPaciente;
import med.voll.api.domain.paciente.ConvenioPacienteRepository;
import med.voll.api.domain.paciente.PacienteRepository;
import med.voll.api.exception.ValidacaoException;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
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

    public AgendaDeConsultas(ConsultaRepository consultaRepository,
                             MedicoRepository medicoRepository,
                             PacienteRepository pacienteRepository,
                             DisponibilidadeMedicoRepository disponibilidadeRepository,
                             ConvenioRepository convenioRepository,
                             MedicoConvenioRepository medicoConvenioRepository,
                             ConvenioPacienteRepository convenioPacienteRepository) {
        this.consultaRepository = consultaRepository;
        this.medicoRepository = medicoRepository;
        this.pacienteRepository = pacienteRepository;
        this.disponibilidadeRepository = disponibilidadeRepository;
        this.convenioRepository = convenioRepository;
        this.medicoConvenioRepository = medicoConvenioRepository;
        this.convenioPacienteRepository = convenioPacienteRepository;
    }

    public DadosDetalhamentoConsulta agendar(DadosAgendamentoConsulta dados) {

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
            validarPacienteComOutraConsultaNoDia(dados);
        }

        // Escolhe o médico (se não foi informado e não é retorno, será aleatório)
        if (medico == null) {
            medico = escolherMedicoAleatorio(dados);
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
            Convenio convenio = convenioRepository.findById(dados.convenioId())
                    .orElseThrow(() -> new ValidacaoException("Convênio informado não encontrado."));
            if (!convenio.isAtivo()) {
                throw new ValidacaoException("Convênio informado está inativo.");
            }
            if (!medicoConvenioRepository.existsByMedicoIdAndConvenioIdAndAtivoTrue(medico.getId(), dados.convenioId())) {
                throw new ValidacaoException("Médico não aceita o convênio informado.");
            }
            if (convenio.getTipo() == TipoConvenio.PLANO) {
                var vinculoPaciente = convenioPacienteRepository
                        .findByPacienteIdAndConvenioId(dados.idPaciente(), dados.convenioId())
                        .filter(ConvenioPaciente::isAtivo)
                        .orElseThrow(() -> new ValidacaoException("Paciente não possui vínculo ativo com o convênio informado."));
                if (vinculoPaciente.getValidade() != null
                        && vinculoPaciente.getValidade().isBefore(dados.data().toLocalDate())) {
                    throw new ValidacaoException("A carteirinha do paciente neste convênio está vencida para a data da consulta.");
                }
            }
            consulta.setConvenio(convenio);
        }

        consultaRepository.save(consulta);

        // 4. Retorno
        return new DadosDetalhamentoConsulta(consulta);
    }

    // ------------------ MÉTODO DE CANCELAMENTO ------------------

    public void cancelar(DadosCancelamentoConsulta dados) {
        var consulta = consultaRepository.findById(dados.idConsulta())
                .orElseThrow(() -> new ValidacaoException("ID da consulta informado não existe!"));

        if (consulta.getDataHora().isBefore(LocalDateTime.now().plusHours(24))) {
            throw new ValidacaoException("Consulta somente pode ser cancelada com antecedência mínima de 24 horas.");
        }

        consulta.cancelar(dados.motivo(), dados.canceladoPor());
    }

    // ------------------ MÉTODOS DE VALIDAÇÃO DE REGRA DE NEGÓCIO ------------------

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

    private void validarPacienteComOutraConsultaNoDia(DadosAgendamentoConsulta dados) {
        var primeiroHorario = dados.data().withHour(7).withMinute(0);
        var ultimoHorario = dados.data().withHour(19).withMinute(0);

        if (consultaRepository.existsByPacienteIdAndDataHoraBetweenAndAtivoTrue(dados.idPaciente(), primeiroHorario, ultimoHorario)) {
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

        if (!origem.isAtivo()) {
            throw new ValidacaoException("A consulta de origem está cancelada e não permite retorno.");
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

    private Medico escolherMedicoAleatorio(DadosAgendamentoConsulta dados) {
        var diaSemana = dados.data().getDayOfWeek().name();
        var hora = dados.data().toLocalTime();
        Optional<Medico> medicoOptional = medicoRepository.escolherMedicoAleatorioLivreNaData(
                dados.data(), diaSemana, hora);
        return medicoOptional.orElse(null);
    }
}
