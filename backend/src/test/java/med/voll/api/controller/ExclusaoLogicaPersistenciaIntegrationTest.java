package med.voll.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import med.voll.api.domain.convenio.Convenio;
import med.voll.api.domain.convenio.ConvenioRepository;
import med.voll.api.domain.convenio.DadosCadastroConvenio;
import med.voll.api.domain.convenio.TipoConvenio;
import med.voll.api.domain.endereco.DadosEndereco;
import med.voll.api.domain.medico.*;
import med.voll.api.domain.paciente.*;
import med.voll.api.domain.usuario.DadosAutenticacao;
import med.voll.api.domain.usuario.Perfil;
import med.voll.api.domain.usuario.Usuario;
import med.voll.api.domain.usuario.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ExclusaoLogicaPersistenciaIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired EspecialidadeRepository especialidadeRepository;
    @Autowired MedicoRepository medicoRepository;
    @Autowired PacienteRepository pacienteRepository;
    @Autowired ConvenioRepository convenioRepository;
    @Autowired ConvenioPacienteRepository convenioPacienteRepository;
    @Autowired DisponibilidadeMedicoRepository disponibilidadeRepository;
    @Autowired MedicoConvenioRepository medicoConvenioRepository;
    @Autowired UsuarioRepository usuarioRepository;

    private final List<Long> usuariosCriados = new ArrayList<>();

    @AfterEach
    void limparDados() {
        convenioPacienteRepository.deleteAllInBatch();
        medicoConvenioRepository.deleteAllInBatch();
        disponibilidadeRepository.deleteAllInBatch();
        pacienteRepository.deleteAllInBatch();
        medicoRepository.deleteAllInBatch();
        usuariosCriados.forEach(usuarioRepository::deleteById);
        usuariosCriados.clear();
        convenioRepository.deleteAllInBatch();
        especialidadeRepository.deleteAllInBatch();
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

    private Paciente criarPacientePersistido() {
        var endereco = new DadosEndereco("Rua B", "Centro", "74000000", "Goiânia", "GO", "Ap 10", "200");
        var cadastro = new DadosCadastroPaciente(
                "Carlos Integracao",
                "carlos.integracao@vollmed.com",
                "62988887777",
                "12345678901",
                endereco
        );
        return pacienteRepository.save(new Paciente(cadastro));
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

    @Test
    @DisplayName("Inativar médico deve bloquear tanto JWT já emitido (401) quanto novo login (403)")
    void deveBloquearLoginETokenAtivoAposInativarMedico() throws Exception {
        var medico = criarMedicoPersistido();
        var usuarioMedico = new Usuario();
        usuarioMedico.setLogin("medico.bloqueio@vollmed.com");
        usuarioMedico.setSenha(passwordEncoder.encode("senha123"));
        usuarioMedico.setRole(Perfil.ROLE_MEDICO);
        usuarioMedico = usuarioRepository.save(usuarioMedico);
        usuariosCriados.add(usuarioMedico.getId());

        medico.setUsuario(usuarioMedico);
        medicoRepository.save(medico);

        var credenciais = objectMapper.writeValueAsString(new DadosAutenticacao("medico.bloqueio@vollmed.com", "senha123"));

        var loginResult = mvc.perform(post("/auth/login")
                        .with(req -> { req.setRemoteAddr("10.10.10.1"); return req; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credenciais))
                .andExpect(status().isOk())
                .andReturn();

        var token = objectMapper.readTree(loginResult.getResponse().getContentAsString()).get("tokenJWT").asText();

        mvc.perform(get("/consultas")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mvc.perform(delete("/medicos/{id}", medico.getId())
                        .with(user(usuarioFuncionario())))
                .andExpect(status().isNoContent());

        mvc.perform(get("/consultas")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.erro").value("Conta de usuário inativa"));

        mvc.perform(post("/auth/login")
                        .with(req -> { req.setRemoteAddr("10.10.10.2"); return req; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credenciais))
                .andExpect(status().isForbidden())
                .andExpect(content().string("Conta de usuário inativa"));
    }

    @Test
    @DisplayName("Reassociar convênio previamente removido do paciente deve reativar o vínculo sem violar unicidade")
    void deveReativarVinculoDeConvenioPacienteAposRemocao() throws Exception {
        var paciente = criarPacientePersistido();
        var convenio = convenioRepository.save(
                new Convenio(new DadosCadastroConvenio("Bradesco Integração", "ANS554433", TipoConvenio.PLANO)));

        var primeiroCadastro = new DadosCadastroConvenioPaciente(convenio.getId(), "CART-001", LocalDate.of(2027, 12, 31));
        var createResult = mvc.perform(post("/pacientes/{pacienteId}/convenios", paciente.getId())
                        .with(user(usuarioFuncionario()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(primeiroCadastro)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.numeroCarteirinha").value("CART-001"))
                .andReturn();

        long vinculoId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asLong();

        mvc.perform(delete("/pacientes/{pacienteId}/convenios/{vinculoId}", paciente.getId(), vinculoId)
                        .with(user(usuarioFuncionario())))
                .andExpect(status().isNoContent());

        var reassociacao = new DadosCadastroConvenioPaciente(convenio.getId(), "CART-002", LocalDate.of(2028, 6, 30));
        mvc.perform(post("/pacientes/{pacienteId}/convenios", paciente.getId())
                        .with(user(usuarioFuncionario()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reassociacao)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(vinculoId))
                .andExpect(jsonPath("$.numeroCarteirinha").value("CART-002"));

        var recarregado = convenioPacienteRepository.findByPacienteIdAndConvenioId(paciente.getId(), convenio.getId()).orElseThrow();
        assertThat(recarregado.isAtivo()).isTrue();
        assertThat(recarregado.getNumeroCarteirinha()).isEqualTo("CART-002");
        assertThat(recarregado.getValidade()).isEqualTo(LocalDate.of(2028, 6, 30));
    }
}
