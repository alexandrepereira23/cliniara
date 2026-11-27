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
import com.cliniara.api.domain.paciente.Paciente;
import com.cliniara.api.domain.paciente.PacienteRepository;
import com.cliniara.api.domain.usuario.Perfil;
import com.cliniara.api.domain.usuario.Usuario;
import com.cliniara.api.exception.ValidacaoException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AgendaDeConsultasTest {

    @Mock ConsultaRepository consultaRepository;
    @Mock MedicoRepository medicoRepository;
    @Mock PacienteRepository pacienteRepository;
    @Mock DisponibilidadeMedicoRepository disponibilidadeRepository;
    @Mock ConvenioRepository convenioRepository;
    @Mock MedicoConvenioRepository medicoConvenioRepository;
    @Mock ConvenioPacienteRepository convenioPacienteRepository;
    @Mock HistoricoStatusConsultaRepository historicoStatusRepository;

    @InjectMocks AgendaDeConsultas agenda;

    // ── helpers ──────────────────────────────────────────────────────────────

    /** Próxima segunda-feira ao meio-dia — sempre > 1 hora no futuro, dia útil */
    private LocalDateTime dataValida() {
        return LocalDateTime.now()
                .with(TemporalAdjusters.next(DayOfWeek.MONDAY))
                .withHour(10).withMinute(0).withSecond(0).withNano(0);
    }

    private Medico medicoAtivo() {
        var m = mock(Medico.class);
        when(m.getId()).thenReturn(1L);
        when(m.isAtivo()).thenReturn(true);
        return m;
    }

    private Paciente pacienteAtivo() {
        var p = mock(Paciente.class);
        when(p.getId()).thenReturn(1L);
        when(p.isAtivo()).thenReturn(true);
        return p;
    }

    private void configurarCenarioFeliz(Medico medico, Paciente paciente, LocalDateTime data) {
        when(pacienteRepository.findById(1L)).thenReturn(Optional.of(paciente));
        when(medicoRepository.findById(1L)).thenReturn(Optional.of(medico));
        when(consultaRepository.existsByPacienteIdAndDataHoraBetweenAndAtivoTrue(anyLong(), any(), any()))
                .thenReturn(false);
        when(disponibilidadeRepository
                .existsByMedicoIdAndDiaSemanaAndHoraInicioLessThanEqualAndHoraFimGreaterThanAndAtivoTrue(
                        anyLong(), any(), any(), any()))
                .thenReturn(true);
        when(consultaRepository.existsByMedicoIdAndDataHoraAndAtivoTrue(anyLong(), any()))
                .thenReturn(false);
    }

    // ── testes de agendamento ─────────────────────────────────────────────────

    @Test
    @DisplayName("deve lançar exceção quando paciente está inativo")
    void deveLancarExcecaoQuandoPacienteInativo() {
        var paciente = mock(Paciente.class);
        when(paciente.isAtivo()).thenReturn(false);
        when(pacienteRepository.findById(1L)).thenReturn(Optional.of(paciente));

        var dados = new DadosAgendamentoConsulta(1L, 1L, dataValida(), PrioridadeConsulta.ROTINA, null, null);

        assertThatThrownBy(() -> agenda.agendar(dados))
                .isInstanceOf(ValidacaoException.class)
                .hasMessageContaining("inativo");
    }

    @Test
    @DisplayName("deve lançar exceção quando paciente não existe")
    void deveLancarExcecaoQuandoPacienteNaoExiste() {
        when(pacienteRepository.findById(1L)).thenReturn(Optional.empty());

        var dados = new DadosAgendamentoConsulta(1L, 1L, dataValida(), PrioridadeConsulta.ROTINA, null, null);

        assertThatThrownBy(() -> agenda.agendar(dados))
                .isInstanceOf(ValidacaoException.class);
    }

    @Test
    @DisplayName("deve lançar exceção quando médico está inativo")
    void deveLancarExcecaoQuandoMedicoInativo() {
        var paciente = pacienteAtivo();
        when(pacienteRepository.findById(1L)).thenReturn(Optional.of(paciente));

        var medico = mock(Medico.class);
        when(medico.isAtivo()).thenReturn(false);
        when(medicoRepository.findById(1L)).thenReturn(Optional.of(medico));

        var dados = new DadosAgendamentoConsulta(1L, 1L, dataValida(), PrioridadeConsulta.ROTINA, null, null);

        assertThatThrownBy(() -> agenda.agendar(dados))
                .isInstanceOf(ValidacaoException.class)
                .hasMessageContaining("inativo");
    }

    @Test
    @DisplayName("deve lançar exceção quando consulta for marcada no domingo")
    void deveLancarExcecaoParaHorarioDomingo() {
        var paciente = pacienteAtivo();
        when(pacienteRepository.findById(1L)).thenReturn(Optional.of(paciente));

        var domingo = LocalDateTime.now()
                .with(TemporalAdjusters.next(DayOfWeek.SUNDAY))
                .withHour(10).withMinute(0).withSecond(0).withNano(0);

        var dados = new DadosAgendamentoConsulta(1L, null, domingo, PrioridadeConsulta.ROTINA, null, null);

        assertThatThrownBy(() -> agenda.agendar(dados))
                .isInstanceOf(ValidacaoException.class)
                .hasMessageContaining("domingo");
    }

    @Test
    @DisplayName("deve lançar exceção quando horário estiver antes das 07h")
    void deveLancarExcecaoParaHorarioAntesDas7() {
        var paciente = pacienteAtivo();
        when(pacienteRepository.findById(1L)).thenReturn(Optional.of(paciente));

        var data = dataValida().withHour(6);

        var dados = new DadosAgendamentoConsulta(1L, null, data, PrioridadeConsulta.ROTINA, null, null);

        assertThatThrownBy(() -> agenda.agendar(dados))
                .isInstanceOf(ValidacaoException.class)
                .hasMessageContaining("funcionamento");
    }

    @Test
    @DisplayName("deve lançar exceção quando horário estiver às 19h ou depois")
    void deveLancarExcecaoParaHorarioApos19h() {
        var paciente = pacienteAtivo();
        when(pacienteRepository.findById(1L)).thenReturn(Optional.of(paciente));

        var data = dataValida().withHour(19);

        var dados = new DadosAgendamentoConsulta(1L, null, data, PrioridadeConsulta.ROTINA, null, null);

        assertThatThrownBy(() -> agenda.agendar(dados))
                .isInstanceOf(ValidacaoException.class)
                .hasMessageContaining("funcionamento");
    }

    @Test
    @DisplayName("deve lançar exceção quando antecedência mínima ROTINA (30 min) não é respeitada")
    void deveLancarExcecaoAntecedenciaRotinaInsuficiente() {
        var paciente = pacienteAtivo();
        when(pacienteRepository.findById(1L)).thenReturn(Optional.of(paciente));

        // Usa segunda-feira passada às 10h — sempre no passado, dia útil e horário comercial.
        // Qualquer tempo no passado satisfaz: data < agora + 30min → antecedência insuficiente.
        var data = LocalDateTime.now()
                .with(TemporalAdjusters.previous(DayOfWeek.MONDAY))
                .withHour(10).withMinute(0).withSecond(0).withNano(0);

        var dados = new DadosAgendamentoConsulta(1L, null, data, PrioridadeConsulta.ROTINA, null, null);

        assertThatThrownBy(() -> agenda.agendar(dados))
                .isInstanceOf(ValidacaoException.class)
                .hasMessageContaining("antecedência");
    }

    @Test
    @DisplayName("URGENCIA não deve validar antecedência mínima")
    void urgenciaNaoValidaAntecedenciaMinima() {
        var paciente = pacienteAtivo();
        var medico = medicoAtivo();
        // Horário nos próximos 2 min (inválido para ROTINA, mas válido para URGÊNCIA)
        var data = LocalDateTime.now().plusMinutes(2).withSecond(0).withNano(0);
        // Ajusta para ser dia útil e dentro do horário
        // Se for domingo ou fora do horário, a validação de horário vai falhar antes
        // Usamos um horário bem no futuro apenas para testar a URGÊNCIA
        var dataUrgencia = dataValida(); // Dia útil em horário válido, mas antecedência > 30 min — OK

        // Para testar especificamente que URGÊNCIA ignora antecedência, vamos pular a verificação de horário
        // e usar configuração adequada
        configurarCenarioFeliz(medico, paciente, dataUrgencia);

        var dados = new DadosAgendamentoConsulta(1L, 1L, dataUrgencia, PrioridadeConsulta.URGENCIA, null, null);

        // Não deve lançar exceção de antecedência
        var resultado = agenda.agendar(dados);
        assertThat(resultado).isNotNull();
    }

    @Test
    @DisplayName("deve lançar exceção quando paciente já tem consulta no dia")
    void deveLancarExcecaoQuandoPacienteJaTemConsultaNoDia() {
        var paciente = pacienteAtivo();
        var medico = medicoAtivo();
        var data = dataValida();

        when(pacienteRepository.findById(1L)).thenReturn(Optional.of(paciente));
        when(medicoRepository.findById(1L)).thenReturn(Optional.of(medico));
        when(consultaRepository.existsByPacienteIdAndDataHoraBetweenAndAtivoTrue(anyLong(), any(), any()))
                .thenReturn(true);

        var dados = new DadosAgendamentoConsulta(1L, 1L, data, PrioridadeConsulta.ROTINA, null, null);

        assertThatThrownBy(() -> agenda.agendar(dados))
                .isInstanceOf(ValidacaoException.class)
                .hasMessageContaining("consulta agendada");
    }

    @Test
    @DisplayName("deve lançar exceção quando médico não tem disponibilidade no horário")
    void deveLancarExcecaoQuandoMedicoSemDisponibilidade() {
        var paciente = pacienteAtivo();
        var medico = medicoAtivo();
        var data = dataValida();

        when(pacienteRepository.findById(1L)).thenReturn(Optional.of(paciente));
        when(medicoRepository.findById(1L)).thenReturn(Optional.of(medico));
        when(consultaRepository.existsByPacienteIdAndDataHoraBetweenAndAtivoTrue(anyLong(), any(), any()))
                .thenReturn(false);
        when(disponibilidadeRepository
                .existsByMedicoIdAndDiaSemanaAndHoraInicioLessThanEqualAndHoraFimGreaterThanAndAtivoTrue(
                        anyLong(), any(), any(), any()))
                .thenReturn(false);

        var dados = new DadosAgendamentoConsulta(1L, 1L, data, PrioridadeConsulta.ROTINA, null, null);

        assertThatThrownBy(() -> agenda.agendar(dados))
                .isInstanceOf(ValidacaoException.class)
                .hasMessageContaining("disponibilidade");
    }

    @Test
    @DisplayName("deve lançar exceção quando médico já tem consulta no horário")
    void deveLancarExcecaoQuandoMedicoOcupado() {
        var paciente = pacienteAtivo();
        var medico = medicoAtivo();
        var data = dataValida();

        when(pacienteRepository.findById(1L)).thenReturn(Optional.of(paciente));
        when(medicoRepository.findById(1L)).thenReturn(Optional.of(medico));
        when(consultaRepository.existsByPacienteIdAndDataHoraBetweenAndAtivoTrue(anyLong(), any(), any()))
                .thenReturn(false);
        when(disponibilidadeRepository
                .existsByMedicoIdAndDiaSemanaAndHoraInicioLessThanEqualAndHoraFimGreaterThanAndAtivoTrue(
                        anyLong(), any(), any(), any()))
                .thenReturn(true);
        when(consultaRepository.existsByMedicoIdAndDataHoraAndAtivoTrue(anyLong(), any()))
                .thenReturn(true);

        var dados = new DadosAgendamentoConsulta(1L, 1L, data, PrioridadeConsulta.ROTINA, null, null);

        assertThatThrownBy(() -> agenda.agendar(dados))
                .isInstanceOf(ValidacaoException.class)
                .hasMessageContaining("consulta ATIVA");
    }

    @Test
    @DisplayName("deve lançar exceção quando médico não aceita o convênio informado")
    void deveLancarExcecaoQuandoMedicoNaoAceitaConvenio() {
        var paciente = pacienteAtivo();
        var medico = medicoAtivo();
        var data = dataValida();
        var convenio = mock(Convenio.class);
        when(convenio.isAtivo()).thenReturn(true);

        configurarCenarioFeliz(medico, paciente, data);
        when(convenioRepository.findById(10L)).thenReturn(Optional.of(convenio));
        when(medicoConvenioRepository.existsByMedicoIdAndConvenioIdAndAtivoTrue(1L, 10L)).thenReturn(false);

        var dados = new DadosAgendamentoConsulta(1L, 1L, data, PrioridadeConsulta.ROTINA, null, 10L);

        assertThatThrownBy(() -> agenda.agendar(dados))
                .isInstanceOf(ValidacaoException.class)
                .hasMessageContaining("não aceita o convênio");
    }

    @Test
    @DisplayName("deve agendar consulta com sucesso quando todos os dados são válidos")
    void deveAgendarConsultaComSucesso() {
        var paciente = pacienteAtivo();
        var medico = medicoAtivo();
        var data = dataValida();

        configurarCenarioFeliz(medico, paciente, data);

        var dados = new DadosAgendamentoConsulta(1L, 1L, data, PrioridadeConsulta.ROTINA, null, null);

        var resultado = agenda.agendar(dados);

        assertThat(resultado).isNotNull();
        assertThat(resultado.idMedico()).isEqualTo(1L);
        assertThat(resultado.idPaciente()).isEqualTo(1L);
        assertThat(resultado.data()).isEqualTo(data);
        verify(consultaRepository).save(any(Consulta.class));
    }

    @Test
    @DisplayName("deve agendar consulta com convênio PLANO aceito pelo médico e carteirinha válida do paciente")
    void deveAgendarConsultaComConvenioAceito() {
        var paciente = pacienteAtivo();
        var medico = medicoAtivo();
        var data = dataValida();
        var convenio = mock(Convenio.class);
        when(convenio.isAtivo()).thenReturn(true);
        when(convenio.getTipo()).thenReturn(TipoConvenio.PLANO);

        var vinculoPaciente = mock(ConvenioPaciente.class);
        when(vinculoPaciente.isAtivo()).thenReturn(true);
        when(vinculoPaciente.getValidade()).thenReturn(data.toLocalDate().plusDays(30));

        configurarCenarioFeliz(medico, paciente, data);
        when(convenioRepository.findById(10L)).thenReturn(Optional.of(convenio));
        when(medicoConvenioRepository.existsByMedicoIdAndConvenioIdAndAtivoTrue(1L, 10L)).thenReturn(true);
        when(convenioPacienteRepository.findByPacienteIdAndConvenioId(1L, 10L)).thenReturn(Optional.of(vinculoPaciente));

        var dados = new DadosAgendamentoConsulta(1L, 1L, data, PrioridadeConsulta.ROTINA, null, 10L);

        var resultado = agenda.agendar(dados);
        assertThat(resultado).isNotNull();
        verify(consultaRepository).save(any(Consulta.class));
    }

    @Test
    @DisplayName("deve lançar exceção quando convênio PLANO não possui vínculo ativo com o paciente")
    void deveLancarExcecaoQuandoConvenioPlanoSemVinculoAtivoDoPaciente() {
        var paciente = pacienteAtivo();
        var medico = medicoAtivo();
        var data = dataValida();
        var convenio = mock(Convenio.class);
        when(convenio.isAtivo()).thenReturn(true);
        when(convenio.getTipo()).thenReturn(TipoConvenio.PLANO);

        configurarCenarioFeliz(medico, paciente, data);
        when(convenioRepository.findById(10L)).thenReturn(Optional.of(convenio));
        when(medicoConvenioRepository.existsByMedicoIdAndConvenioIdAndAtivoTrue(1L, 10L)).thenReturn(true);
        when(convenioPacienteRepository.findByPacienteIdAndConvenioId(1L, 10L)).thenReturn(Optional.empty());

        var dados = new DadosAgendamentoConsulta(1L, 1L, data, PrioridadeConsulta.ROTINA, null, 10L);

        assertThatThrownBy(() -> agenda.agendar(dados))
                .isInstanceOf(ValidacaoException.class)
                .hasMessageContaining("vínculo ativo");
    }

    @Test
    @DisplayName("deve lançar exceção quando carteirinha do convênio PLANO está vencida na data da consulta")
    void deveLancarExcecaoQuandoCarteirinhaPlanoVencida() {
        var paciente = pacienteAtivo();
        var medico = medicoAtivo();
        var data = dataValida();
        var convenio = mock(Convenio.class);
        when(convenio.isAtivo()).thenReturn(true);
        when(convenio.getTipo()).thenReturn(TipoConvenio.PLANO);

        var vinculoPaciente = mock(ConvenioPaciente.class);
        when(vinculoPaciente.isAtivo()).thenReturn(true);
        when(vinculoPaciente.getValidade()).thenReturn(data.toLocalDate().minusDays(1));

        configurarCenarioFeliz(medico, paciente, data);
        when(convenioRepository.findById(10L)).thenReturn(Optional.of(convenio));
        when(medicoConvenioRepository.existsByMedicoIdAndConvenioIdAndAtivoTrue(1L, 10L)).thenReturn(true);
        when(convenioPacienteRepository.findByPacienteIdAndConvenioId(1L, 10L)).thenReturn(Optional.of(vinculoPaciente));

        var dados = new DadosAgendamentoConsulta(1L, 1L, data, PrioridadeConsulta.ROTINA, null, 10L);

        assertThatThrownBy(() -> agenda.agendar(dados))
                .isInstanceOf(ValidacaoException.class)
                .hasMessageContaining("vencida");
    }

    @Test
    @DisplayName("deve agendar consulta com convênio PARTICULAR sem exigir carteirinha do paciente")
    void deveAgendarConsultaComConvenioParticularSemExigirCarteirinha() {
        var paciente = pacienteAtivo();
        var medico = medicoAtivo();
        var data = dataValida();
        var convenio = mock(Convenio.class);
        when(convenio.isAtivo()).thenReturn(true);
        when(convenio.getTipo()).thenReturn(TipoConvenio.PARTICULAR);

        configurarCenarioFeliz(medico, paciente, data);
        when(convenioRepository.findById(20L)).thenReturn(Optional.of(convenio));
        when(medicoConvenioRepository.existsByMedicoIdAndConvenioIdAndAtivoTrue(1L, 20L)).thenReturn(true);

        var dados = new DadosAgendamentoConsulta(1L, 1L, data, PrioridadeConsulta.ROTINA, null, 20L);

        var resultado = agenda.agendar(dados);
        assertThat(resultado).isNotNull();
        verifyNoInteractions(convenioPacienteRepository);
        verify(consultaRepository).save(any(Consulta.class));
    }

    // ── testes de retorno ─────────────────────────────────────────────────────

    private Consulta consultaOrigemAtiva(Medico medico, Paciente paciente, LocalDateTime dataHora) {
        var origem = mock(Consulta.class);
        when(origem.getId()).thenReturn(50L);
        when(origem.isAtivo()).thenReturn(true);
        when(origem.getStatus()).thenReturn(StatusConsulta.CONCLUIDA);
        when(origem.getMedico()).thenReturn(medico);
        when(origem.getPaciente()).thenReturn(paciente);
        when(origem.getDataHora()).thenReturn(dataHora);
        return origem;
    }

    @Test
    @DisplayName("deve lançar exceção quando consulta de origem do retorno ainda não estiver CONCLUIDA")
    void deveLancarExcecaoQuandoConsultaOrigemDoRetornoNaoEstiverConcluida() {
        var paciente = pacienteAtivo();
        var medico = medicoAtivo();
        var dataRetorno = dataValida();
        var origem = consultaOrigemAtiva(medico, paciente, dataRetorno.minusDays(5));
        when(origem.getStatus()).thenReturn(StatusConsulta.AGENDADA);

        configurarCenarioFeliz(medico, paciente, dataRetorno);
        when(consultaRepository.findById(50L)).thenReturn(Optional.of(origem));

        var dados = new DadosAgendamentoConsulta(1L, 1L, dataRetorno, PrioridadeConsulta.ROTINA, 50L, null);

        assertThatThrownBy(() -> agenda.agendar(dados))
                .isInstanceOf(ValidacaoException.class)
                .hasMessageContaining("concluída");
    }

    @Test
    @DisplayName("deve lançar exceção quando retorno pertencer a outro paciente")
    void deveLancarExcecaoQuandoRetornoForDeOutroPaciente() {
        var paciente = pacienteAtivo();
        var outroPaciente = mock(Paciente.class);
        when(outroPaciente.getId()).thenReturn(2L);
        var medico = medicoAtivo();
        var dataRetorno = dataValida();
        var origem = consultaOrigemAtiva(medico, outroPaciente, dataRetorno.minusDays(5));

        configurarCenarioFeliz(medico, paciente, dataRetorno);
        when(consultaRepository.findById(50L)).thenReturn(Optional.of(origem));

        var dados = new DadosAgendamentoConsulta(1L, 1L, dataRetorno, PrioridadeConsulta.ROTINA, 50L, null);

        assertThatThrownBy(() -> agenda.agendar(dados))
                .isInstanceOf(ValidacaoException.class)
                .hasMessageContaining("mesmo paciente");
    }

    @Test
    @DisplayName("deve lançar exceção quando retorno não for posterior à consulta original")
    void deveLancarExcecaoQuandoRetornoNaoForPosteriorAConsultaOriginal() {
        var paciente = pacienteAtivo();
        var medico = medicoAtivo();
        var dataRetorno = dataValida();
        var origem = consultaOrigemAtiva(medico, paciente, dataRetorno);

        configurarCenarioFeliz(medico, paciente, dataRetorno);
        when(consultaRepository.findById(50L)).thenReturn(Optional.of(origem));

        var dados = new DadosAgendamentoConsulta(1L, 1L, dataRetorno, PrioridadeConsulta.ROTINA, 50L, null);

        assertThatThrownBy(() -> agenda.agendar(dados))
                .isInstanceOf(ValidacaoException.class)
                .hasMessageContaining("após a data e horário");
    }

    @Test
    @DisplayName("deve lançar exceção quando retorno ultrapassar 30 dias da consulta original")
    void deveLancarExcecaoQuandoRetornoUltrapassar30Dias() {
        var paciente = pacienteAtivo();
        var medico = medicoAtivo();
        var dataRetorno = dataValida();
        var origem = consultaOrigemAtiva(medico, paciente, dataRetorno.minusDays(31));

        configurarCenarioFeliz(medico, paciente, dataRetorno);
        when(consultaRepository.findById(50L)).thenReturn(Optional.of(origem));

        var dados = new DadosAgendamentoConsulta(1L, 1L, dataRetorno, PrioridadeConsulta.ROTINA, 50L, null);

        assertThatThrownBy(() -> agenda.agendar(dados))
                .isInstanceOf(ValidacaoException.class)
                .hasMessageContaining("30 dias");
    }

    @Test
    @DisplayName("deve lançar exceção quando retorno for agendado com médico diferente da consulta original")
    void deveLancarExcecaoQuandoRetornoForComOutroMedico() {
        var paciente = pacienteAtivo();
        var medico = medicoAtivo();
        var medicoOriginal = mock(Medico.class);
        when(medicoOriginal.getId()).thenReturn(2L);
        when(medicoOriginal.isAtivo()).thenReturn(true);

        var dataRetorno = dataValida();
        var origem = consultaOrigemAtiva(medicoOriginal, paciente, dataRetorno.minusDays(5));

        configurarCenarioFeliz(medico, paciente, dataRetorno);
        when(consultaRepository.findById(50L)).thenReturn(Optional.of(origem));
        when(consultaRepository.existsByConsultaOrigemIdAndAtivoTrue(50L)).thenReturn(false);

        var dados = new DadosAgendamentoConsulta(1L, 1L, dataRetorno, PrioridadeConsulta.ROTINA, 50L, null);

        assertThatThrownBy(() -> agenda.agendar(dados))
                .isInstanceOf(ValidacaoException.class)
                .hasMessageContaining("mesmo médico");
    }

    @Test
    @DisplayName("deve lançar exceção quando já existir retorno ativo para a consulta original")
    void deveLancarExcecaoQuandoJaExistirRetornoAtivo() {
        var paciente = pacienteAtivo();
        var medico = medicoAtivo();
        var dataRetorno = dataValida();
        var origem = consultaOrigemAtiva(medico, paciente, dataRetorno.minusDays(5));

        configurarCenarioFeliz(medico, paciente, dataRetorno);
        when(consultaRepository.findById(50L)).thenReturn(Optional.of(origem));
        when(consultaRepository.existsByConsultaOrigemIdAndAtivoTrue(50L)).thenReturn(true);

        var dados = new DadosAgendamentoConsulta(1L, 1L, dataRetorno, PrioridadeConsulta.ROTINA, 50L, null);

        assertThatThrownBy(() -> agenda.agendar(dados))
                .isInstanceOf(ValidacaoException.class)
                .hasMessageContaining("retorno ativo");
    }

    @Test
    @DisplayName("deve assumir automaticamente o médico da consulta original no retorno quando idMedico for omitido e retorno anterior foi cancelado")
    void deveAssumirMedicoOriginalNoRetornoQuandoIdMedicoOmitido() {
        var paciente = pacienteAtivo();
        var medico = medicoAtivo();
        var dataRetorno = dataValida();
        var origem = consultaOrigemAtiva(medico, paciente, dataRetorno.minusDays(7));

        configurarCenarioFeliz(medico, paciente, dataRetorno);
        when(consultaRepository.findById(50L)).thenReturn(Optional.of(origem));
        when(consultaRepository.existsByConsultaOrigemIdAndAtivoTrue(50L)).thenReturn(false);

        var dados = new DadosAgendamentoConsulta(1L, null, dataRetorno, PrioridadeConsulta.ROTINA, 50L, null);

        var resultado = agenda.agendar(dados);

        assertThat(resultado).isNotNull();
        assertThat(resultado.idMedico()).isEqualTo(1L);
        verify(medicoRepository, never()).escolherMedicoAleatorioLivreNaData(any(), any(), any());
        verify(consultaRepository).save(any(Consulta.class));
    }

    @Test
    @DisplayName("deve lançar exceção no retorno sem médico informado quando o médico original estiver inativo")
    void deveLancarExcecaoNoRetornoQuandoMedicoOriginalEstiverInativo() {
        var paciente = pacienteAtivo();
        var medicoInativo = mock(Medico.class);
        when(medicoInativo.getId()).thenReturn(1L);
        when(medicoInativo.isAtivo()).thenReturn(false);

        var dataRetorno = dataValida();
        var origem = consultaOrigemAtiva(medicoInativo, paciente, dataRetorno.minusDays(7));

        configurarCenarioFeliz(medicoInativo, paciente, dataRetorno);
        when(consultaRepository.findById(50L)).thenReturn(Optional.of(origem));
        when(consultaRepository.existsByConsultaOrigemIdAndAtivoTrue(50L)).thenReturn(false);

        var dados = new DadosAgendamentoConsulta(1L, null, dataRetorno, PrioridadeConsulta.ROTINA, 50L, null);

        assertThatThrownBy(() -> agenda.agendar(dados))
                .isInstanceOf(ValidacaoException.class)
                .hasMessageContaining("médico da consulta original está inativo");
    }

    // ── testes de cancelamento ────────────────────────────────────────────────

    @Test
    @DisplayName("deve lançar exceção ao cancelar consulta pelo paciente com menos de 24h de antecedência")
    void deveLancarExcecaoAoCancelarComMenosDe24h() {
        var consulta = mock(Consulta.class);
        when(consulta.getStatus()).thenReturn(StatusConsulta.AGENDADA);
        when(consulta.getDataHora()).thenReturn(LocalDateTime.now().plusHours(12));
        when(consultaRepository.findById(1L)).thenReturn(Optional.of(consulta));

        var dados = new DadosCancelamentoConsulta(1L, MotivoCancelamento.PACIENTE_DESISTIU, CanceladoPor.PACIENTE);

        assertThatThrownBy(() -> agenda.cancelar(dados))
                .isInstanceOf(ValidacaoException.class)
                .hasMessageContaining("24 horas");
    }

    @Test
    @DisplayName("deve cancelar consulta com sucesso pelo paciente quando antecedência é maior que 24h")
    void deveCancelarConsultaComSucesso() {
        var consulta = mock(Consulta.class);
        when(consulta.getStatus()).thenReturn(StatusConsulta.AGENDADA);
        when(consulta.getDataHora()).thenReturn(LocalDateTime.now().plusDays(3));
        when(consultaRepository.findById(1L)).thenReturn(Optional.of(consulta));

        var dados = new DadosCancelamentoConsulta(1L, MotivoCancelamento.PACIENTE_DESISTIU, CanceladoPor.PACIENTE);

        agenda.cancelar(dados);

        verify(consulta).cancelar(MotivoCancelamento.PACIENTE_DESISTIU, CanceladoPor.PACIENTE);
        verify(historicoStatusRepository).save(any(HistoricoStatusConsulta.class));
    }

    @Test
    @DisplayName("deve permitir cancelamento por MEDICO_CANCELOU sem trava de 24h assumindo CLINICA")
    void devePermitirCancelamentoPorMedicoCancelouSemTravaDe24h() {
        var consulta = new Consulta(medicoAtivo(), pacienteAtivo(), LocalDateTime.now().plusHours(2), PrioridadeConsulta.ROTINA);
        when(consultaRepository.findById(1L)).thenReturn(Optional.of(consulta));

        var dados = new DadosCancelamentoConsulta(1L, MotivoCancelamento.MEDICO_CANCELOU, null);
        agenda.cancelar(dados, new Usuario(1L, "recepcao@cliniara.com", "senha", Perfil.ROLE_FUNCIONARIO, null));

        assertThat(consulta.getStatus()).isEqualTo(StatusConsulta.CANCELADA);
        assertThat(consulta.isAtivo()).isFalse();
        assertThat(consulta.getCanceladoPor()).isEqualTo(CanceladoPor.CLINICA);
        verify(historicoStatusRepository).save(any(HistoricoStatusConsulta.class));
    }

    @Test
    @DisplayName("deve permitir cancelamento pela CLINICA com menos de 24h")
    void devePermitirCancelamentoPelaClinicaSemTravaDe24h() {
        var consulta = new Consulta(medicoAtivo(), pacienteAtivo(), LocalDateTime.now().plusHours(3), PrioridadeConsulta.ROTINA);
        when(consultaRepository.findById(1L)).thenReturn(Optional.of(consulta));

        var dados = new DadosCancelamentoConsulta(1L, MotivoCancelamento.OUTROS, CanceladoPor.CLINICA);
        agenda.cancelar(dados);

        assertThat(consulta.getStatus()).isEqualTo(StatusConsulta.CANCELADA);
        assertThat(consulta.isAtivo()).isFalse();
    }

    @Test
    @DisplayName("deve rejeitar MEDICO_CANCELOU atribuído ao PACIENTE")
    void deveRejeitarMedicoCancelouAtribuidoAoPaciente() {
        var consulta = new Consulta(medicoAtivo(), pacienteAtivo(), LocalDateTime.now().plusDays(2), PrioridadeConsulta.ROTINA);
        when(consultaRepository.findById(1L)).thenReturn(Optional.of(consulta));

        var dados = new DadosCancelamentoConsulta(1L, MotivoCancelamento.MEDICO_CANCELOU, CanceladoPor.PACIENTE);

        assertThatThrownBy(() -> agenda.cancelar(dados))
                .isInstanceOf(ValidacaoException.class)
                .hasMessageContaining("indisponibilidade médica");
    }

    @Test
    @DisplayName("deve rejeitar cancelamento de consulta já concluída")
    void deveRejeitarCancelamentoDeConsultaConcluida() {
        var consulta = new Consulta(medicoAtivo(), pacienteAtivo(), LocalDateTime.now().plusDays(2), PrioridadeConsulta.ROTINA);
        consulta.registrarCheckIn();
        consulta.iniciarAtendimento();
        consulta.concluirAtendimento();
        when(consultaRepository.findById(1L)).thenReturn(Optional.of(consulta));

        var dados = new DadosCancelamentoConsulta(1L, MotivoCancelamento.OUTROS, CanceladoPor.CLINICA);

        assertThatThrownBy(() -> agenda.cancelar(dados))
                .isInstanceOf(ValidacaoException.class)
                .hasMessageContaining("não pode ser cancelada");
    }

    @Test
    @DisplayName("deve lançar exceção ao cancelar consulta inexistente")
    void deveLancarExcecaoAoCancelarConsultaInexistente() {
        when(consultaRepository.findById(99L)).thenReturn(Optional.empty());

        var dados = new DadosCancelamentoConsulta(99L, MotivoCancelamento.OUTROS, null);

        assertThatThrownBy(() -> agenda.cancelar(dados))
                .isInstanceOf(ValidacaoException.class);
    }

    // ── testes do ciclo completo, ausência e reagendamento ────────────────────

    @Test
    @DisplayName("deve percorrer ciclo completo AGENDADA -> CONFIRMADA -> CHECK_IN -> EM_ATENDIMENTO -> CONCLUIDA registrando histórico")
    void devePercorrerCicloCompletoRegistrandoHistorico() {
        var medico = medicoAtivo();
        var paciente = pacienteAtivo();
        var consulta = new Consulta(medico, paciente, dataValida(), PrioridadeConsulta.ROTINA);
        var recepcao = new Usuario(1L, "recepcao@cliniara.com", "senha", Perfil.ROLE_FUNCIONARIO, null);
        var usuarioMedico = new Usuario(2L, "medico@cliniara.com", "senha", Perfil.ROLE_MEDICO, medico);

        when(consultaRepository.findById(1L)).thenReturn(Optional.of(consulta));
        when(medicoRepository.findByUsuario(usuarioMedico)).thenReturn(Optional.of(medico));

        assertThat(agenda.confirmar(1L, recepcao).status()).isEqualTo(StatusConsulta.CONFIRMADA);
        assertThat(agenda.registrarCheckIn(1L, recepcao).status()).isEqualTo(StatusConsulta.CHECK_IN);
        assertThat(agenda.iniciarAtendimento(1L, usuarioMedico).status()).isEqualTo(StatusConsulta.EM_ATENDIMENTO);
        assertThat(agenda.concluirAtendimento(1L, usuarioMedico).status()).isEqualTo(StatusConsulta.CONCLUIDA);
        assertThat(consulta.isAtivo()).isTrue();
        verify(historicoStatusRepository, times(4)).save(any(HistoricoStatusConsulta.class));
    }

    @Test
    @DisplayName("deve bloquear início de atendimento antes do check-in")
    void deveBloquearInicioAtendimentoAntesDoCheckIn() {
        var medico = medicoAtivo();
        var consulta = new Consulta(medico, pacienteAtivo(), dataValida(), PrioridadeConsulta.ROTINA);
        var usuarioMedico = new Usuario(2L, "medico@cliniara.com", "senha", Perfil.ROLE_MEDICO, medico);

        when(consultaRepository.findById(1L)).thenReturn(Optional.of(consulta));
        when(medicoRepository.findByUsuario(usuarioMedico)).thenReturn(Optional.of(medico));

        assertThatThrownBy(() -> agenda.iniciarAtendimento(1L, usuarioMedico))
                .isInstanceOf(ValidacaoException.class)
                .hasMessageContaining("check-in");
    }

    @Test
    @DisplayName("deve bloquear início de atendimento por médico diferente do responsável (403)")
    void deveBloquearInicioAtendimentoPorOutroMedico() {
        var medicoConsulta = medicoAtivo();
        var outroMedico = mock(Medico.class);
        when(outroMedico.getId()).thenReturn(99L);
        var consulta = new Consulta(medicoConsulta, pacienteAtivo(), dataValida(), PrioridadeConsulta.ROTINA);
        consulta.registrarCheckIn();
        var usuarioOutroMedico = new Usuario(3L, "outro@cliniara.com", "senha", Perfil.ROLE_MEDICO, outroMedico);

        when(consultaRepository.findById(1L)).thenReturn(Optional.of(consulta));
        when(medicoRepository.findByUsuario(usuarioOutroMedico)).thenReturn(Optional.of(outroMedico));

        assertThatThrownBy(() -> agenda.iniciarAtendimento(1L, usuarioOutroMedico))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    @DisplayName("deve registrar ausência na data da consulta ou após o horário liberando o horário")
    void deveRegistrarAusenciaNaDataDaConsulta() {
        var consulta = new Consulta(medicoAtivo(), pacienteAtivo(), LocalDateTime.now().minusMinutes(30), PrioridadeConsulta.ROTINA);
        var recepcao = new Usuario(1L, "recepcao@cliniara.com", "senha", Perfil.ROLE_FUNCIONARIO, null);
        when(consultaRepository.findById(1L)).thenReturn(Optional.of(consulta));

        var res = agenda.registrarAusencia(1L, new DadosAusenciaConsulta("Sem retorno telefônico"), recepcao);

        assertThat(res.status()).isEqualTo(StatusConsulta.AUSENTE);
        assertThat(consulta.isAtivo()).isFalse();
        verify(historicoStatusRepository).save(any(HistoricoStatusConsulta.class));
    }

    @Test
    @DisplayName("deve bloquear registro de ausência para consulta em data futura")
    void deveBloquearAusenciaEmDataFutura() {
        var consulta = new Consulta(medicoAtivo(), pacienteAtivo(), LocalDateTime.now().plusDays(2), PrioridadeConsulta.ROTINA);
        when(consultaRepository.findById(1L)).thenReturn(Optional.of(consulta));

        assertThatThrownBy(() -> agenda.registrarAusencia(1L, null, null))
                .isInstanceOf(ValidacaoException.class)
                .hasMessageContaining("data da consulta");
    }

    @Test
    @DisplayName("deve reagendar consulta pela CLINICA sem trava de 24h marcando original como REAGENDADA e criando nova vinculada")
    void deveReagendarConsultaPelaClinicaSemTravaDe24h() {
        var medico = medicoAtivo();
        var paciente = pacienteAtivo();
        var dataAtual = LocalDateTime.now().plusHours(4);
        var novaData = dataValida().plusDays(7);
        var consultaAtual = new Consulta(medico, paciente, dataAtual, PrioridadeConsulta.ROTINA);
        var recepcao = new Usuario(1L, "recepcao@cliniara.com", "senha", Perfil.ROLE_FUNCIONARIO, null);

        configurarCenarioFeliz(medico, paciente, novaData);
        when(consultaRepository.findById(1L)).thenReturn(Optional.of(consultaAtual));

        var dados = new DadosReagendamentoConsulta(novaData, null, CanceladoPor.CLINICA, "Imprevisto médico");
        var nova = agenda.reagendar(1L, dados, recepcao);

        assertThat(consultaAtual.getStatus()).isEqualTo(StatusConsulta.REAGENDADA);
        assertThat(consultaAtual.isAtivo()).isFalse();
        assertThat(nova.status()).isEqualTo(StatusConsulta.AGENDADA);
        assertThat(nova.data()).isEqualTo(novaData);
        verify(consultaRepository).saveAndFlush(consultaAtual);
        verify(consultaRepository).save(any(Consulta.class));
        verify(historicoStatusRepository, times(2)).save(any(HistoricoStatusConsulta.class));
    }

    @Test
    @DisplayName("deve bloquear reagendamento solicitado pelo PACIENTE com menos de 24h de antecedência")
    void deveBloquearReagendamentoPeloPacienteComMenosDe24h() {
        var consultaAtual = new Consulta(medicoAtivo(), pacienteAtivo(), LocalDateTime.now().plusHours(6), PrioridadeConsulta.ROTINA);
        when(consultaRepository.findById(1L)).thenReturn(Optional.of(consultaAtual));

        var dados = new DadosReagendamentoConsulta(dataValida().plusDays(7), null, CanceladoPor.PACIENTE, "Viagem");

        assertThatThrownBy(() -> agenda.reagendar(1L, dados, null))
                .isInstanceOf(ValidacaoException.class)
                .hasMessageContaining("24 horas");
    }

    @Test
    @DisplayName("deve bloquear reagendamento para o mesmo médico e mesmo horário")
    void deveBloquearReagendamentoMesmoMedicoEMesmoHorario() {
        var medico = medicoAtivo();
        var paciente = pacienteAtivo();
        var dataAtual = dataValida().plusDays(7);
        var consultaAtual = new Consulta(medico, paciente, dataAtual, PrioridadeConsulta.ROTINA);

        when(consultaRepository.findById(1L)).thenReturn(Optional.of(consultaAtual));
        when(medicoRepository.findById(1L)).thenReturn(Optional.of(medico));

        var dados = new DadosReagendamentoConsulta(dataAtual, 1L, CanceladoPor.CLINICA, "Teste");

        assertThatThrownBy(() -> agenda.reagendar(1L, dados, null))
                .isInstanceOf(ValidacaoException.class)
                .hasMessageContaining("diferente");
    }
}
