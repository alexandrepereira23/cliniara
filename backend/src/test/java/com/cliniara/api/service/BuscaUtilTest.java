package com.cliniara.api.service;

import com.cliniara.api.domain.consulta.AgendaDeConsultas;
import com.cliniara.api.domain.consulta.ConsultaRepository;
import com.cliniara.api.domain.consulta.HistoricoStatusConsultaRepository;
import com.cliniara.api.domain.medico.MedicoRepository;
import com.cliniara.api.domain.usuario.Perfil;
import com.cliniara.api.domain.usuario.Usuario;
import com.cliniara.api.exception.ValidacaoException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class BuscaUtilTest {

    @Test
    @DisplayName("deve normalizar texto e escapar curingas LIKE com caractere !")
    void deveNormalizarEEscaparCuringas() {
        assertThat(BuscaUtil.paraLikeContendo(null)).isNull();
        assertThat(BuscaUtil.paraLikeContendo("   ")).isNull();
        assertThat(BuscaUtil.paraLikeContendo("  Maria 100%_!  ")).isEqualTo("%maria 100!%!_!!%");
        assertThat(BuscaUtil.paraLikeSomenteDigitos("123.456-78")).isEqualTo("%12345678%");
        assertThat(BuscaUtil.paraLikeSomenteDigitos("Maria")).isNull();
    }

    @Test
    @DisplayName("ConsultaService deve rejeitar período com dataInicio posterior a dataFim")
    void consultaServiceDeveRejeitarPeriodoInvalido() {
        var service = new ConsultaService(
                mock(AgendaDeConsultas.class),
                mock(ConsultaRepository.class),
                mock(HistoricoStatusConsultaRepository.class),
                mock(MedicoRepository.class)
        );
        var usuario = new Usuario(1L, "func@cliniara.com", "senha", Perfil.ROLE_FUNCIONARIO, null);

        assertThatThrownBy(() -> service.listar(
                PageRequest.of(0, 10),
                usuario,
                null,
                false,
                null,
                null,
                LocalDate.of(2026, 11, 20),
                LocalDate.of(2026, 11, 10)
        ))
                .isInstanceOf(ValidacaoException.class)
                .hasMessageContaining("posterior");
    }
}
