package med.voll.api.controller;

import med.voll.api.domain.convenio.Convenio;
import med.voll.api.domain.convenio.ConvenioRepository;
import med.voll.api.domain.convenio.DadosCadastroConvenio;
import med.voll.api.domain.convenio.TipoConvenio;
import med.voll.api.domain.endereco.DadosEndereco;
import med.voll.api.domain.medico.*;
import med.voll.api.domain.usuario.Perfil;
import med.voll.api.domain.usuario.Usuario;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.time.DayOfWeek;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ExclusaoLogicaPersistenciaIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired EspecialidadeRepository especialidadeRepository;
    @Autowired MedicoRepository medicoRepository;
    @Autowired ConvenioRepository convenioRepository;
    @Autowired DisponibilidadeMedicoRepository disponibilidadeRepository;
    @Autowired MedicoConvenioRepository medicoConvenioRepository;

    @AfterEach
    void limparDados() {
        medicoConvenioRepository.deleteAll();
        disponibilidadeRepository.deleteAll();
        medicoRepository.deleteAll();
        convenioRepository.deleteAll();
        especialidadeRepository.deleteAll();
    }

    private Usuario usuarioFuncionario() {
        return new Usuario(900L, "funcionario-integracao@vollmed.com", "senha", Perfil.ROLE_FUNCIONARIO, null);
    }

    private Medico criarMedicoPersistido() {
        var especialidade = especialidadeRepository.save(new EspecialidadeEntity("Cardiologia Integração"));
        var endereco = new DadosEndereco("Rua A", "Centro", "74000000", "Goiânia", "GO", "Sala 1", "100");
        var cadastro = new DadosCadastroMedico(
                "Dra. Helena Costa",
                "helena.integracao@vollmed.com",
                "62999990000",
                "987654",
                especialidade.getId(),
                endereco
        );
        return medicoRepository.save(new Medico(cadastro, especialidade));
    }

    @Test
    @DisplayName("DELETE /medicos/{id}/disponibilidade/{dispId} deve persistir ativo=false no banco")
    void devePersistirInativacaoDeDisponibilidadeNoBanco() throws Exception {
        var medico = criarMedicoPersistido();
        var disp = disponibilidadeRepository.save(
                new DisponibilidadeMedico(medico, DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(12, 0)));

        mvc.perform(delete("/medicos/{medicoId}/disponibilidade/{dispId}", medico.getId(), disp.getId())
                        .with(user(usuarioFuncionario())))
                .andExpect(status().isNoContent());

        var recarregada = disponibilidadeRepository.findById(disp.getId()).orElseThrow();
        assertThat(recarregada.isAtivo()).isFalse();

        mvc.perform(get("/medicos/{medicoId}/disponibilidade", medico.getId())
                        .with(user(usuarioFuncionario())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("DELETE /medicos/{id}/convenios/{convenioId} deve persistir ativo=false no banco")
    void devePersistirDesvinculoDeConvenioMedicoNoBanco() throws Exception {
        var medico = criarMedicoPersistido();
        var convenio = convenioRepository.save(
                new Convenio(new DadosCadastroConvenio("Unimed Integração", "ANS998877", TipoConvenio.PLANO)));
        var vinculo = medicoConvenioRepository.save(new MedicoConvenio(medico, convenio));

        mvc.perform(delete("/medicos/{medicoId}/convenios/{convenioId}", medico.getId(), convenio.getId())
                        .with(user(usuarioFuncionario())))
                .andExpect(status().isNoContent());

        var recarregado = medicoConvenioRepository.findById(vinculo.getId()).orElseThrow();
        assertThat(recarregado.isAtivo()).isFalse();

        mvc.perform(get("/medicos/{medicoId}/convenios", medico.getId())
                        .with(user(usuarioFuncionario())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
