package com.cliniara.api.controller;

import com.cliniara.api.domain.dashboard.DadosDashboardGestor;
import com.cliniara.api.domain.dashboard.DadosDashboardMedico;
import com.cliniara.api.domain.dashboard.DadosDashboardRecepcao;
import com.cliniara.api.domain.usuario.Usuario;
import com.cliniara.api.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("dashboard")
@Tag(name = "Dashboard", description = "Indicadores agregados operacionais, clínicos e gerenciais")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/recepcao")
    @PreAuthorize("hasRole('ROLE_FUNCIONARIO')")
    @Operation(summary = "Dashboard da recepção", description = "Retorna resumo diário por status, próxima consulta e agenda do dia")
    public ResponseEntity<DadosDashboardRecepcao> recepcao(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data
    ) {
        return ResponseEntity.ok(dashboardService.obterDashboardRecepcao(data));
    }

    @GetMapping("/medico")
    @PreAuthorize("hasRole('ROLE_MEDICO')")
    @Operation(summary = "Dashboard do médico", description = "Retorna indicadores reais da agenda própria, fila de check-in e totais assistenciais")
    public ResponseEntity<DadosDashboardMedico> medico(
            @AuthenticationPrincipal Usuario usuario,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data
    ) {
        return ResponseEntity.ok(dashboardService.obterDashboardMedico(usuario, data));
    }

    @GetMapping("/gestor")
    @PreAuthorize("hasAnyRole('ROLE_GESTOR', 'ROLE_AUDITOR')")
    @Operation(summary = "Dashboard gerencial", description = "Retorna indicadores agregados por período, status e especialidade sem expor dados clínicos individuais")
    public ResponseEntity<DadosDashboardGestor> gestor(
            @RequestParam(defaultValue = "30") int dias
    ) {
        return ResponseEntity.ok(dashboardService.obterDashboardGestor(dias));
    }
}
