package com.cliniara.api.controller;

import com.cliniara.api.config.MethodSecurityTestConfig;
import com.cliniara.api.domain.dashboard.*;
import com.cliniara.api.domain.usuario.Perfil;
import com.cliniara.api.domain.usuario.Usuario;
import com.cliniara.api.domain.usuario.UsuarioRepository;
import com.cliniara.api.infra.security.TokenService;
import com.cliniara.api.service.DashboardService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DashboardController.class)
@Import(MethodSecurityTestConfig.class)
class DashboardControllerTest {

    @Autowired MockMvc mvc;

    @MockBean DashboardService dashboardService;
    @MockBean TokenService tokenService;
    @MockBean UsuarioRepository usuarioRepository;
    @MockBean JpaMetamodelMappingContext jpaMetamodelMappingContext;

    private Usuario usuario(Perfil role) {
        return new Usuario(1L, role.name().toLowerCase() + "@cliniara.com", "senha", role, null);
    }

    @Test
    @DisplayName("ROLE_FUNCIONARIO deve acessar GET /dashboard/recepcao")
    void funcionarioDeveAcessarDashboardRecepcao() throws Exception {
        when(dashboardService.obterDashboardRecepcao(any())).thenReturn(
                new DadosDashboardRecepcao(LocalDate.now(), 4, 1, 1, 1, 0, 1, 0, 0, null, List.of())
        );

        mvc.perform(get("/dashboard/recepcao").with(user(usuario(Perfil.ROLE_FUNCIONARIO))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consultasHoje").value(4));
    }

    @Test
    @DisplayName("ROLE_MEDICO não deve acessar GET /dashboard/recepcao — deve receber 403")
    void medicoNaoDeveAcessarDashboardRecepcao() throws Exception {
        mvc.perform(get("/dashboard/recepcao").with(user(usuario(Perfil.ROLE_MEDICO))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ROLE_MEDICO deve acessar GET /dashboard/medico")
    void medicoDeveAcessarDashboardMedico() throws Exception {
        when(dashboardService.obterDashboardMedico(any(), any())).thenReturn(
                new DadosDashboardMedico(LocalDate.now(), 3, 1, 2, 0, 0, 5, 10, 20, 15, List.of())
        );

        mvc.perform(get("/dashboard/medico").with(user(usuario(Perfil.ROLE_MEDICO))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filaEsperaHoje").value(2));
    }

    @Test
    @DisplayName("ROLE_FUNCIONARIO não deve acessar GET /dashboard/medico — deve receber 403")
    void funcionarioNaoDeveAcessarDashboardMedico() throws Exception {
        mvc.perform(get("/dashboard/medico").with(user(usuario(Perfil.ROLE_FUNCIONARIO))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ROLE_GESTOR deve acessar GET /dashboard/gestor com parâmetro dias")
    void gestorDeveAcessarDashboardGestor() throws Exception {
        when(dashboardService.obterDashboardGestor(eq(7))).thenReturn(
                new DadosDashboardGestor(7, 2, 10, 8, 1, 1, 0, 10.0, 10.0, 5, 40, List.of(), List.of())
        );

        mvc.perform(get("/dashboard/gestor").param("dias", "7").with(user(usuario(Perfil.ROLE_GESTOR))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.periodoDias").value(7))
                .andExpect(jsonPath("$.taxaCancelamentoPercentual").value(10.0));
    }

    @Test
    @DisplayName("ROLE_ADMIN não deve acessar GET /dashboard/gestor — deve receber 403")
    void adminNaoDeveAcessarDashboardGestor() throws Exception {
        mvc.perform(get("/dashboard/gestor").with(user(usuario(Perfil.ROLE_ADMIN))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("requisição não autenticada em /dashboard/recepcao deve retornar 401")
    void deveRejeitarNaoAutenticado() throws Exception {
        mvc.perform(get("/dashboard/recepcao"))
                .andExpect(status().isUnauthorized());
    }
}
