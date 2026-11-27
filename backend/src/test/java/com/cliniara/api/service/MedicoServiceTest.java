package com.cliniara.api.service;

import com.cliniara.api.domain.consulta.ConsultaRepository;
import com.cliniara.api.domain.medico.EspecialidadeRepository;
import com.cliniara.api.domain.medico.Medico;
import com.cliniara.api.domain.medico.MedicoRepository;
import com.cliniara.api.exception.ConflitoException;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MedicoServiceTest {

    @Mock MedicoRepository medicoRepository;
    @Mock EspecialidadeRepository especialidadeRepository;
    @Mock ConsultaRepository consultaRepository;

    @InjectMocks MedicoService medicoService;

    @Test
    @DisplayName("deve lançar ConflitoException (409) ao tentar inativar médico com consultas futuras ou em atendimento")
    void deveBloquearInativacaoQuandoHaConsultasPendentes() {
        var medico = mock(Medico.class);
        when(medicoRepository.findById(1L)).thenReturn(Optional.of(medico));
        when(consultaRepository.countConsultasPendentesOuFuturasDoMedico(eq(1L), any())).thenReturn(2L);

        assertThatThrownBy(() -> medicoService.excluir(1L))
                .isInstanceOf(ConflitoException.class)
                .hasMessageContaining("2 consulta(s)");

        verify(medico, never()).excluir();
    }

    @Test
    @DisplayName("deve inativar médico quando não houver consultas futuras ou em atendimento")
    void deveInativarMedicoSemConsultasPendentes() {
        var medico = mock(Medico.class);
        when(medicoRepository.findById(1L)).thenReturn(Optional.of(medico));
        when(consultaRepository.countConsultasPendentesOuFuturasDoMedico(eq(1L), any())).thenReturn(0L);

        medicoService.excluir(1L);

        verify(medico).excluir();
    }

    @Test
    @DisplayName("deve lançar EntityNotFoundException ao tentar inativar médico inexistente")
    void deveLancarNotFoundQuandoMedicoInexistente() {
        when(medicoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> medicoService.excluir(99L))
                .isInstanceOf(EntityNotFoundException.class);
    }
}
