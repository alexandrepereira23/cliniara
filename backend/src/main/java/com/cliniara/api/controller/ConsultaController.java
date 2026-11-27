package com.cliniara.api.controller;

import com.cliniara.api.domain.consulta.*;
import com.cliniara.api.domain.usuario.Usuario;
import com.cliniara.api.service.ConsultaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("consultas")
@Tag(name = "Consultas", description = "Endpoints para gerenciamento do ciclo completo de consultas")
public class ConsultaController {

    private final ConsultaService consultaService;

    public ConsultaController(ConsultaService consultaService) {
        this.consultaService = consultaService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ROLE_FUNCIONARIO')")
    @Operation(summary = "Agendar consulta", description = "Agenda uma nova consulta ou retorno no sistema")
    public ResponseEntity<DadosDetalhamentoConsulta> agendar(
            @RequestBody @Valid DadosAgendamentoConsulta dados,
            @AuthenticationPrincipal Usuario usuario
    ) {
        var detalhamento = consultaService.agendar(dados, usuario);
        return ResponseEntity.ok(detalhamento);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ROLE_FUNCIONARIO', 'ROLE_MEDICO', 'ROLE_AUDITOR', 'ROLE_GESTOR')")
    @Operation(summary = "Listar consultas", description = "Lista consultas paginadas com filtro opcional por status ou inclusão de encerradas")
    public ResponseEntity<Page<DadosListagemConsulta>> listar(
            @ParameterObject @PageableDefault(size = 10, sort = {"dataHora"}) Pageable paginacao,
            @RequestParam(required = false) StatusConsulta status,
            @RequestParam(defaultValue = "false") boolean incluirEncerradas,
            @AuthenticationPrincipal Usuario usuario
    ) {
        var page = consultaService.listar(paginacao, usuario, status, incluirEncerradas);
        return ResponseEntity.ok(page);
    }

    @GetMapping("/{id}/historico")
    @PreAuthorize("hasAnyRole('ROLE_FUNCIONARIO', 'ROLE_MEDICO', 'ROLE_AUDITOR', 'ROLE_GESTOR')")
    @Operation(summary = "Histórico de status da consulta", description = "Lista a trilha cronológica de transições de status da consulta")
    public ResponseEntity<List<DadosHistoricoStatusConsulta>> listarHistorico(
            @PathVariable Long id,
            @AuthenticationPrincipal Usuario usuario
    ) {
        return ResponseEntity.ok(consultaService.listarHistorico(id, usuario));
    }

    @PatchMapping("/{id}/confirmar")
    @PreAuthorize("hasRole('ROLE_FUNCIONARIO')")
    @Operation(summary = "Confirmar consulta", description = "Transiciona consulta de AGENDADA para CONFIRMADA")
    public ResponseEntity<DadosDetalhamentoConsulta> confirmar(
            @PathVariable Long id,
            @AuthenticationPrincipal Usuario usuario
    ) {
        return ResponseEntity.ok(consultaService.confirmar(id, usuario));
    }

    @PatchMapping("/{id}/check-in")
    @PreAuthorize("hasRole('ROLE_FUNCIONARIO')")
    @Operation(summary = "Registrar check-in", description = "Transiciona consulta de AGENDADA ou CONFIRMADA para CHECK_IN")
    public ResponseEntity<DadosDetalhamentoConsulta> registrarCheckIn(
            @PathVariable Long id,
            @AuthenticationPrincipal Usuario usuario
    ) {
        return ResponseEntity.ok(consultaService.registrarCheckIn(id, usuario));
    }

    @PatchMapping("/{id}/iniciar")
    @PreAuthorize("hasRole('ROLE_MEDICO')")
    @Operation(summary = "Iniciar atendimento", description = "Transiciona consulta de CHECK_IN para EM_ATENDIMENTO pelo médico responsável")
    public ResponseEntity<DadosDetalhamentoConsulta> iniciarAtendimento(
            @PathVariable Long id,
            @AuthenticationPrincipal Usuario usuario
    ) {
        return ResponseEntity.ok(consultaService.iniciarAtendimento(id, usuario));
    }

    @PatchMapping("/{id}/concluir")
    @PreAuthorize("hasRole('ROLE_MEDICO')")
    @Operation(summary = "Concluir atendimento", description = "Transiciona consulta de EM_ATENDIMENTO para CONCLUIDA pelo médico responsável")
    public ResponseEntity<DadosDetalhamentoConsulta> concluirAtendimento(
            @PathVariable Long id,
            @AuthenticationPrincipal Usuario usuario
    ) {
        return ResponseEntity.ok(consultaService.concluirAtendimento(id, usuario));
    }

    @PatchMapping("/{id}/ausencia")
    @PreAuthorize("hasRole('ROLE_FUNCIONARIO')")
    @Operation(summary = "Registrar ausência", description = "Marca consulta como AUSENTE (no-show) na data da consulta ou após o horário")
    public ResponseEntity<DadosDetalhamentoConsulta> registrarAusencia(
            @PathVariable Long id,
            @RequestBody(required = false) @Valid DadosAusenciaConsulta dados,
            @AuthenticationPrincipal Usuario usuario
    ) {
        return ResponseEntity.ok(consultaService.registrarAusencia(id, dados, usuario));
    }

    @PostMapping("/{id}/reagendar")
    @PreAuthorize("hasRole('ROLE_FUNCIONARIO')")
    @Operation(summary = "Reagendar consulta", description = "Marca a consulta atual como REAGENDADA e cria uma nova consulta vinculada")
    public ResponseEntity<DadosDetalhamentoConsulta> reagendar(
            @PathVariable Long id,
            @RequestBody @Valid DadosReagendamentoConsulta dados,
            @AuthenticationPrincipal Usuario usuario
    ) {
        return ResponseEntity.ok(consultaService.reagendar(id, dados, usuario));
    }

    @DeleteMapping
    @PreAuthorize("hasRole('ROLE_FUNCIONARIO')")
    @Operation(summary = "Cancelar consulta", description = "Cancela uma consulta agendada (exige 24h apenas quando solicitado pelo paciente)")
    public ResponseEntity<Void> cancelar(
            @RequestBody @Valid DadosCancelamentoConsulta dados,
            @AuthenticationPrincipal Usuario usuario
    ) {
        consultaService.cancelar(dados, usuario);
        return ResponseEntity.noContent().build();
    }
}
