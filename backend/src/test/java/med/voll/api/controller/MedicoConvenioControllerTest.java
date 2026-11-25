package med.voll.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import med.voll.api.config.MethodSecurityTestConfig;
import med.voll.api.domain.convenio.Convenio;
import med.voll.api.domain.convenio.ConvenioRepository;
import med.voll.api.domain.medico.DadosVinculoConvenioMedico;
import med.voll.api.domain.medico.Medico;
import med.voll.api.domain.medico.MedicoConvenio;
import med.voll.api.domain.medico.MedicoConvenioRepository;
import med.voll.api.domain.medico.MedicoRepository;
import med.voll.api.domain.usuario.Perfil;
import med.voll.api.domain.usuario.Usuario;
import med.voll.api.domain.usuario.UsuarioRepository;
import med.voll.api.infra.security.TokenService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MedicoConvenioController.class)
@Import(MethodSecurityTestConfig.class)
class MedicoConvenioControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean MedicoRepository medicoRepository;
    @MockBean ConvenioRepository convenioRepository;
    @MockBean MedicoConvenioRepository medicoConvenioRepository;
    @MockBean TokenService tokenService;
    @MockBean UsuarioRepository usuarioRepository;
    @MockBean JpaMetamodelMappingContext jpaMetamodelMappingContext;

    private Usuario usuarioFuncionario() {
        return new Usuario(1L, "func@test.com", "senha", Perfil.ROLE_FUNCIONARIO, null);
    }

    private Usuario usuarioAdmin() {
        return new Usuario(2L, "admin@test.com", "senha", Perfil.ROLE_ADMIN, null);
    }

    @Test
    @DisplayName("ROLE_ADMIN nao deve vincular convenio ao medico e deve receber 403")
    void naoDeveVincularConvenioComAdmin() throws Exception {
        mvc.perform(post("/medicos/1/convenios")
                        .with(user(usuarioAdmin())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DadosVinculoConvenioMedico(1L))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ROLE_FUNCIONARIO deve listar convenios do medico")
    void deveListarConveniosDoMedicoComFuncionario() throws Exception {
        when(medicoConvenioRepository.findAllByMedicoIdAndAtivoTrue(anyLong())).thenReturn(List.of());

        mvc.perform(get("/medicos/1/convenios")
                        .with(user(usuarioFuncionario())))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("ROLE_ADMIN nao deve listar convenios do medico e deve receber 403")
    void naoDeveListarConveniosDoMedicoComAdmin() throws Exception {
        mvc.perform(get("/medicos/1/convenios")
                        .with(user(usuarioAdmin())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ROLE_ADMIN nao deve desvincular convenio do medico e deve receber 403")
    void naoDeveDesvincularConvenioComAdmin() throws Exception {
        mvc.perform(delete("/medicos/1/convenios/1")
                        .with(user(usuarioAdmin())).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ROLE_FUNCIONARIO deve desvincular convenio do medico e persistir inativação")
    void deveDesvincularConvenioDoMedicoComFuncionario() throws Exception {
        var vinculo = new MedicoConvenio(10L, mock(Medico.class), mock(Convenio.class), true);
        when(medicoConvenioRepository.findByMedicoIdAndConvenioId(1L, 2L)).thenReturn(Optional.of(vinculo));

        mvc.perform(delete("/medicos/1/convenios/2")
                        .with(user(usuarioFuncionario())).with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(vinculo.isAtivo()).isFalse();
        verify(medicoConvenioRepository).save(vinculo);
    }

    @Test
    @DisplayName("deve retornar 404 com JSON padronizado ao desvincular convenio inexistente")
    void deveRetornar404AoDesvincularConvenioInexistente() throws Exception {
        when(medicoConvenioRepository.findByMedicoIdAndConvenioId(1L, 99L)).thenReturn(Optional.empty());

        mvc.perform(delete("/medicos/1/convenios/99")
                        .with(user(usuarioFuncionario())).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.campo").value("nao_encontrado"))
                .andExpect(jsonPath("$.mensagem").value("Vínculo não encontrado"));
    }
}
