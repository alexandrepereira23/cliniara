package com.cliniara.api.controller;

import com.cliniara.api.config.MethodSecurityTestConfig;
import com.cliniara.api.domain.consulta.*;
import com.cliniara.api.domain.usuario.Perfil;
import com.cliniara.api.domain.usuario.Usuario;
import com.cliniara.api.domain.usuario.UsuarioRepository;
import com.cliniara.api.infra.security.TokenService;
import com.cliniara.api.service.ConsultaService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ConsultaController.class)
@Import(MethodSecurityTestConfig.class)
class ConsultaControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean ConsultaService consultaService;
    @MockBean TokenService tokenService;
    @MockBean UsuarioRepository usuarioRepository;
    @MockBean JpaMetamodelMappingContext jpaMetamodelMappingContext;

    // ── helpers ───────────────────────────────────────────────────────────────

    private Usuario usuarioFuncionario() {
        return new Usuario(1L, "func@test.com", "senha", Perfil.ROLE_FUNCIONARIO, null);
    }

    private Usuario usuarioMedico() {
        return new Usuario(2L, "medico@test.com", "senha", Perfil.ROLE_MEDICO, null);
    }

    private Usuario usuarioAdmin() {
        return new Usuario(3L, "admin@test.com", "senha", Perfil.ROLE_ADMIN, null);
    }

    private LocalDateTime dataValida() {
        return LocalDateTime.now()
                .with(TemporalAdjusters.next(DayOfWeek.MONDAY))
                .withHour(10).withMinute(0).withSecond(0).withNano(0);
    }

    private DadosAgendamentoConsulta dadosAgendamento() {
        return new DadosAgendamentoConsulta(1L, 1L, dataValida(), PrioridadeConsulta.ROTINA, null, null);
    }

    private DadosDetalhamentoConsulta detalhamento(StatusConsulta status) {
        return new DadosDetalhamentoConsulta(
                1L, 1L, 1L, dataValida(), PrioridadeConsulta.ROTINA, TipoConsulta.NORMAL, status, null, null, null
        );
    }

    // ── testes ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("ROLE_FUNCIONARIO deve agendar consulta e receber 200 com status AGENDADA")
    void deveAgendarConsultaComFuncionario() throws Exception {
        when(consultaService.agendar(any(), any())).thenReturn(detalhamento(StatusConsulta.AGENDADA));

        mvc.perform(post("/consultas")
                        .with(user(usuarioFuncionario())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dadosAgendamento())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("AGENDADA"));
    }

    @Test
    @DisplayName("ROLE_MEDICO não deve agendar consulta — deve receber 403")
    void naoDeveAgendarConsultaComRoleMedico() throws Exception {
        mvc.perform(post("/consultas")
                        .with(user(usuarioMedico())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dadosAgendamento())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ROLE_FUNCIONARIO deve listar consultas com status, idMedico, idPaciente, consultaOrigemId e consultaReagendadaDeId")
    void deveListarConsultas() throws Exception {
        var item = new DadosListagemConsulta(
                10L, 2L, "Dra. Ana", 3L, "João", dataValida(),
                PrioridadeConsulta.ROTINA, TipoConsulta.RETORNO, StatusConsulta.CONFIRMADA, 5L, 8L
        );
        when(consultaService.listar(any(Pageable.class), any(Usuario.class), isNull(), eq(false)))
                .thenReturn(new PageImpl<>(List.of(item)));

        mvc.perform(get("/consultas")
                        .with(user(usuarioFuncionario())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(10))
                .andExpect(jsonPath("$.content[0].status").value("CONFIRMADA"))
                .andExpect(jsonPath("$.content[0].idMedico").value(2))
                .andExpect(jsonPath("$.content[0].idPaciente").value(3))
                .andExpect(jsonPath("$.content[0].consultaOrigemId").value(5))
                .andExpect(jsonPath("$.content[0].consultaReagendadaDeId").value(8));
    }

    @Test
    @DisplayName("ROLE_FUNCIONARIO deve filtrar consultas por status específico")
    void deveFiltrarConsultasPorStatus() throws Exception {
        when(consultaService.listar(any(Pageable.class), any(Usuario.class), eq(StatusConsulta.CANCELADA), anyBoolean()))
                .thenReturn(new PageImpl<>(List.of()));

        mvc.perform(get("/consultas")
                        .param("status", "CANCELADA")
                        .with(user(usuarioFuncionario())))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("ROLE_ADMIN não deve listar consultas — deve receber 403")
    void naoDeveListarConsultasComAdmin() throws Exception {
        mvc.perform(get("/consultas")
                        .with(user(usuarioAdmin())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ROLE_MEDICO deve listar apenas suas consultas")
    void medicoDeveListarApenasPropriasConsultas() throws Exception {
        when(consultaService.listar(any(Pageable.class), any(Usuario.class), isNull(), eq(false)))
                .thenReturn(new PageImpl<>(List.of()));

        mvc.perform(get("/consultas")
                        .with(user(usuarioMedico())))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("ROLE_FUNCIONARIO deve consultar histórico de status da consulta")
    void deveListarHistoricoComFuncionario() throws Exception {
        var hist = new DadosHistoricoStatusConsulta(
                1L, null, StatusConsulta.AGENDADA, "func@test.com", "Consulta agendada", LocalDateTime.now()
        );
        when(consultaService.listarHistorico(eq(1L), any(Usuario.class))).thenReturn(List.of(hist));

        mvc.perform(get("/consultas/1/historico")
                        .with(user(usuarioFuncionario())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].statusNovo").value("AGENDADA"))
                .andExpect(jsonPath("$[0].usuarioLogin").value("func@test.com"));
    }

    @Test
    @DisplayName("ROLE_ADMIN não deve acessar histórico de consulta — deve receber 403")
    void naoDeveListarHistoricoComAdmin() throws Exception {
        mvc.perform(get("/consultas/1/historico")
                        .with(user(usuarioAdmin())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ROLE_FUNCIONARIO deve confirmar consulta e receber 200")
    void deveConfirmarConsultaComFuncionario() throws Exception {
        when(consultaService.confirmar(eq(1L), any())).thenReturn(detalhamento(StatusConsulta.CONFIRMADA));

        mvc.perform(patch("/consultas/1/confirmar")
                        .with(user(usuarioFuncionario())).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMADA"));
    }

    @Test
    @DisplayName("ROLE_MEDICO não deve confirmar consulta — deve receber 403")
    void naoDeveConfirmarConsultaComMedico() throws Exception {
        mvc.perform(patch("/consultas/1/confirmar")
                        .with(user(usuarioMedico())).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ROLE_FUNCIONARIO deve registrar check-in e receber 200")
    void deveRegistrarCheckInComFuncionario() throws Exception {
        when(consultaService.registrarCheckIn(eq(1L), any())).thenReturn(detalhamento(StatusConsulta.CHECK_IN));

        mvc.perform(patch("/consultas/1/check-in")
                        .with(user(usuarioFuncionario())).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CHECK_IN"));
    }

    @Test
    @DisplayName("ROLE_MEDICO deve iniciar atendimento e receber 200")
    void deveIniciarAtendimentoComMedico() throws Exception {
        when(consultaService.iniciarAtendimento(eq(1L), any())).thenReturn(detalhamento(StatusConsulta.EM_ATENDIMENTO));

        mvc.perform(patch("/consultas/1/iniciar")
                        .with(user(usuarioMedico())).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EM_ATENDIMENTO"));
    }

    @Test
    @DisplayName("ROLE_FUNCIONARIO não deve iniciar atendimento — deve receber 403")
    void naoDeveIniciarAtendimentoComFuncionario() throws Exception {
        mvc.perform(patch("/consultas/1/iniciar")
                        .with(user(usuarioFuncionario())).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ROLE_MEDICO deve concluir atendimento e receber 200")
    void deveConcluirAtendimentoComMedico() throws Exception {
        when(consultaService.concluirAtendimento(eq(1L), any())).thenReturn(detalhamento(StatusConsulta.CONCLUIDA));

        mvc.perform(patch("/consultas/1/concluir")
                        .with(user(usuarioMedico())).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONCLUIDA"));
    }

    @Test
    @DisplayName("ROLE_FUNCIONARIO deve registrar ausência e receber 200")
    void deveRegistrarAusenciaComFuncionario() throws Exception {
        when(consultaService.registrarAusencia(eq(1L), any(), any())).thenReturn(detalhamento(StatusConsulta.AUSENTE));

        mvc.perform(patch("/consultas/1/ausencia")
                        .with(user(usuarioFuncionario())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DadosAusenciaConsulta("Não compareceu"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AUSENTE"));
    }

    @Test
    @DisplayName("ROLE_FUNCIONARIO deve reagendar consulta e receber 200")
    void deveReagendarConsultaComFuncionario() throws Exception {
        var nova = new DadosDetalhamentoConsulta(
                2L, 1L, 1L, dataValida().plusDays(1), PrioridadeConsulta.ROTINA,
                TipoConsulta.NORMAL, StatusConsulta.AGENDADA, null, 1L, null
        );
        when(consultaService.reagendar(eq(1L), any(), any())).thenReturn(nova);

        var body = new DadosReagendamentoConsulta(dataValida().plusDays(1), 1L, CanceladoPor.CLINICA, "Ajuste de agenda");
        mvc.perform(post("/consultas/1/reagendar")
                        .with(user(usuarioFuncionario())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.consultaReagendadaDeId").value(1));
    }

    @Test
    @DisplayName("ROLE_MEDICO não deve reagendar consulta — deve receber 403")
    void naoDeveReagendarConsultaComMedico() throws Exception {
        var body = new DadosReagendamentoConsulta(dataValida().plusDays(1), 1L, CanceladoPor.CLINICA, "Ajuste");
        mvc.perform(post("/consultas/1/reagendar")
                        .with(user(usuarioMedico())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ROLE_FUNCIONARIO deve cancelar consulta e receber 204")
    void deveCancelarConsultaComFuncionario() throws Exception {
        var dados = new DadosCancelamentoConsulta(1L, MotivoCancelamento.PACIENTE_DESISTIU, CanceladoPor.PACIENTE);

        mvc.perform(delete("/consultas")
                        .with(user(usuarioFuncionario())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dados)))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("ROLE_MEDICO não deve cancelar consulta — deve receber 403")
    void naoDeveCancelarConsultaComRoleMedico() throws Exception {
        var dados = new DadosCancelamentoConsulta(1L, MotivoCancelamento.PACIENTE_DESISTIU, null);

        mvc.perform(delete("/consultas")
                        .with(user(usuarioMedico())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dados)))
                .andExpect(status().isForbidden());
    }
}
