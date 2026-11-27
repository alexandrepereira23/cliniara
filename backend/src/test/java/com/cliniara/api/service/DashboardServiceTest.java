package com.cliniara.api.service;

import com.cliniara.api.domain.consulta.Consulta;
import com.cliniara.api.domain.consulta.ConsultaRepository;
import com.cliniara.api.domain.consulta.PrioridadeConsulta;
import com.cliniara.api.domain.consulta.StatusConsulta;
import com.cliniara.api.domain.medico.Medico;
import com.cliniara.api.domain.medico.MedicoRepository;
import com.cliniara.api.domain.paciente.Paciente;
import com.cliniara.api.domain.paciente.PacienteRepository;
import com.cliniara.api.domain.prontuario.ProntuarioRepository;
import com.cliniara.api.domain.usuario.Perfil;
import com.cliniara.api.domain.usuario.Usuario;
import com.cliniara.api.exception.ValidacaoException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock ConsultaRepository consultaRepository;
    @Mock MedicoRepository medicoRepository;
    @Mock PacienteRepository pacienteRepository;
    @Mock ProntuarioRepository prontuarioRepository;

    @InjectMocks DashboardService dashboardService;

    private Medico medicoMock(long id, String nome) {
        var m = mock(Medico.class);
        when(m.getId()).thenReturn(id);
        when(m.getNome()).thenReturn(nome);
        return m;
    }

    private Paciente pacienteMock(long id, String nome) {
        var p = mock(Paciente.class);
        when(p.getId()).thenReturn(id);
        when(p.getNome()).thenReturn(nome);
        return p;
    }

    @Test
    @DisplayName("deve agregar indicadores diários e próxima consulta para o dashboard da recepção")
    void deveObterDashboardRecepcao() {
        LocalDate amanha = LocalDate.now().plusDays(1);
        var medico = medicoMock(1L, "Dra. Ana");
        var pac1 = pacienteMock(10L, "João");
        var pac2 = pacienteMock(11L, "Maria");

        var c1 = new Consulta(medico, pac1, amanha.atTime(9, 0), PrioridadeConsulta.ROTINA);
        c1.registrarCheckIn();
        c1.iniciarAtendimento();
        c1.concluirAtendimento();

        var c2 = new Consulta(medico, pac2, amanha.atTime(10, 30), PrioridadeConsulta.PRIORITARIO);
        c2.confirmar();

        when(consultaRepository.findAllAtivasNoIntervalo(any(), any())).thenReturn(List.of(c1, c2));
        when(consultaRepository.countByStatusNoIntervalo(any(), any())).thenReturn(List.of(
                new Object[]{StatusConsulta.CONCLUIDA, 1L},
                new Object[]{StatusConsulta.CONFIRMADA, 1L},
                new Object[]{StatusConsulta.CANCELADA, 2L},
                new Object[]{StatusConsulta.AUSENTE, 1L}
        ));

        var res = dashboardService.obterDashboardRecepcao(amanha);

        assertThat(res.dataReferencia()).isEqualTo(amanha);
        assertThat(res.consultasHoje()).isEqualTo(2);
        assertThat(res.concluidasHoje()).isEqualTo(1);
        assertThat(res.confirmadasHoje()).isEqualTo(1);
        assertThat(res.canceladasHoje()).isEqualTo(2);
        assertThat(res.ausentesHoje()).isEqualTo(1);
        assertThat(res.proximaConsulta()).isNotNull();
        assertThat(res.proximaConsulta().nomePaciente()).isEqualTo("Maria");
        assertThat(res.agendaHoje()).hasSize(2);
    }

    @Test
    @DisplayName("deve retornar 403 no dashboard médico quando usuário não possui médico vinculado")
    void deveRetornar403NoDashboardMedicoSemVinculo() {
        var usuario = new Usuario(2L, "medico@cliniara.com", "senha", Perfil.ROLE_MEDICO, null);
        when(medicoRepository.findByUsuario(usuario)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> dashboardService.obterDashboardMedico(usuario, LocalDate.now()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    @DisplayName("deve agregar fila de check-in, urgências e totais para o dashboard médico")
    void deveObterDashboardMedico() {
        var medico = medicoMock(5L, "Dr. Carlos");
        var usuario = new Usuario(2L, "carlos@cliniara.com", "senha", Perfil.ROLE_MEDICO, medico);
        var pac = pacienteMock(10L, "Beatriz");

        var c1 = new Consulta(medico, pac, LocalDateTime.now().withHour(10).withMinute(0), PrioridadeConsulta.URGENCIA);
        c1.registrarCheckIn();

        when(medicoRepository.findByUsuario(usuario)).thenReturn(Optional.of(medico));
        when(consultaRepository.findAllAtivasNoIntervaloByMedicoId(eq(5L), any(), any())).thenReturn(List.of(c1));
        when(consultaRepository.countByMedicoIdAndAtivoTrueAndDataHoraGreaterThanEqualAndDataHoraLessThan(eq(5L), any(), any()))
                .thenReturn(8L);
        when(consultaRepository.countPacientesDistintosAtivosByMedicoId(5L)).thenReturn(14L);
        when(consultaRepository.countByMedicoIdAndAtivoTrue(5L)).thenReturn(42L);
        when(prontuarioRepository.countByAtivoTrueAndMedicoId(5L)).thenReturn(30L);

        var res = dashboardService.obterDashboardMedico(usuario, LocalDate.now());

        assertThat(res.consultasHoje()).isEqualTo(1);
        assertThat(res.urgenciasHoje()).isEqualTo(1);
        assertThat(res.filaEsperaHoje()).isEqualTo(1);
        assertThat(res.proximosSeteDias()).isEqualTo(8);
        assertThat(res.totalPacientesVinculados()).isEqualTo(14);
        assertThat(res.totalConsultasAtivas()).isEqualTo(42);
        assertThat(res.totalProntuarios()).isEqualTo(30);
    }

    @Test
    @DisplayName("deve calcular taxas de cancelamento/absenteísmo e produção por especialidade no dashboard gestor")
    void deveObterDashboardGestor() {
        when(consultaRepository.countByStatusNoIntervalo(any(), any()))
                .thenReturn(List.of(
                        new Object[]{StatusConsulta.AGENDADA, 3L},
                        new Object[]{StatusConsulta.CANCELADA, 1L}
                ))
                .thenReturn(List.of(
                        new Object[]{StatusConsulta.CONCLUIDA, 6L},
                        new Object[]{StatusConsulta.CANCELADA, 2L},
                        new Object[]{StatusConsulta.AUSENTE, 1L},
                        new Object[]{StatusConsulta.REAGENDADA, 1L}
                ));
        when(consultaRepository.countProducaoPorEspecialidadeNoIntervalo(any(), any())).thenReturn(List.of(
                new Object[]{"Cardiologia", 7L, 5L, 1L, 1L},
                new Object[]{"Ortopedia", 3L, 1L, 1L, 0L}
        ));
        when(medicoRepository.countByAtivoTrue()).thenReturn(12L);
        when(pacienteRepository.countByAtivoTrue()).thenReturn(150L);

        var res = dashboardService.obterDashboardGestor(30);

        assertThat(res.periodoDias()).isEqualTo(30);
        assertThat(res.consultasHoje()).isEqualTo(3);
        assertThat(res.totalConsultasPeriodo()).isEqualTo(10);
        assertThat(res.concluidasPeriodo()).isEqualTo(6);
        assertThat(res.canceladasPeriodo()).isEqualTo(2);
        assertThat(res.ausentesPeriodo()).isEqualTo(1);
        assertThat(res.reagendadasPeriodo()).isEqualTo(1);
        assertThat(res.taxaCancelamentoPercentual()).isEqualTo(20.0);
        assertThat(res.taxaAbsenteismoPercentual()).isEqualTo(10.0);
        assertThat(res.medicosAtivos()).isEqualTo(12);
        assertThat(res.pacientesAtivos()).isEqualTo(150);
        assertThat(res.porEspecialidade()).hasSize(2);
        assertThat(res.porEspecialidade().get(0).especialidade()).isEqualTo("Cardiologia");
    }

    @Test
    @DisplayName("deve rejeitar período inválido (< 1 ou > 365 dias) no dashboard gestor")
    void deveRejeitarPeriodoInvalidoNoDashboardGestor() {
        assertThatThrownBy(() -> dashboardService.obterDashboardGestor(0))
                .isInstanceOf(ValidacaoException.class)
                .hasMessageContaining("1 e 365");

        assertThatThrownBy(() -> dashboardService.obterDashboardGestor(400))
                .isInstanceOf(ValidacaoException.class);
    }
}
