package com.cliniara.api.service;

import com.cliniara.api.domain.consulta.Consulta;
import com.cliniara.api.domain.consulta.ConsultaRepository;
import com.cliniara.api.domain.consulta.DadosListagemConsulta;
import com.cliniara.api.domain.consulta.PrioridadeConsulta;
import com.cliniara.api.domain.consulta.StatusConsulta;
import com.cliniara.api.domain.dashboard.*;
import com.cliniara.api.domain.medico.MedicoRepository;
import com.cliniara.api.domain.paciente.PacienteRepository;
import com.cliniara.api.domain.prontuario.ProntuarioRepository;
import com.cliniara.api.domain.usuario.Usuario;
import com.cliniara.api.exception.ValidacaoException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class DashboardService {

    private final ConsultaRepository consultaRepository;
    private final MedicoRepository medicoRepository;
    private final PacienteRepository pacienteRepository;
    private final ProntuarioRepository prontuarioRepository;

    public DashboardService(
            ConsultaRepository consultaRepository,
            MedicoRepository medicoRepository,
            PacienteRepository pacienteRepository,
            ProntuarioRepository prontuarioRepository
    ) {
        this.consultaRepository = consultaRepository;
        this.medicoRepository = medicoRepository;
        this.pacienteRepository = pacienteRepository;
        this.prontuarioRepository = prontuarioRepository;
    }

    @Transactional(readOnly = true)
    public DadosDashboardRecepcao obterDashboardRecepcao(LocalDate dataReferencia) {
        LocalDate dia = dataReferencia != null ? dataReferencia : LocalDate.now();
        LocalDateTime inicio = dia.atStartOfDay();
        LocalDateTime fimExclusivo = dia.plusDays(1).atStartOfDay();

        List<Consulta> ativasHoje = consultaRepository.findAllAtivasNoIntervalo(inicio, fimExclusivo);
        Map<StatusConsulta, Long> contagemStatus = mapearContagemPorStatus(
                consultaRepository.countByStatusNoIntervalo(inicio, fimExclusivo)
        );

        LocalDateTime agora = LocalDateTime.now();
        boolean ehHoje = dia.equals(LocalDate.now());
        DadosListagemConsulta proximaConsulta = ativasHoje.stream()
                .filter(c -> c.getStatus() != StatusConsulta.CONCLUIDA)
                .filter(c -> !ehHoje || !c.getDataHora().isBefore(agora))
                .findFirst()
                .map(DadosListagemConsulta::new)
                .orElse(null);

        List<DadosListagemConsulta> agendaHoje = ativasHoje.stream()
                .map(DadosListagemConsulta::new)
                .toList();

        return new DadosDashboardRecepcao(
                dia,
                ativasHoje.size(),
                contagemStatus.getOrDefault(StatusConsulta.AGENDADA, 0L),
                contagemStatus.getOrDefault(StatusConsulta.CONFIRMADA, 0L),
                contagemStatus.getOrDefault(StatusConsulta.CHECK_IN, 0L),
                contagemStatus.getOrDefault(StatusConsulta.EM_ATENDIMENTO, 0L),
                contagemStatus.getOrDefault(StatusConsulta.CONCLUIDA, 0L),
                contagemStatus.getOrDefault(StatusConsulta.CANCELADA, 0L),
                contagemStatus.getOrDefault(StatusConsulta.AUSENTE, 0L),
                proximaConsulta,
                agendaHoje
        );
    }

    @Transactional(readOnly = true)
    public DadosDashboardMedico obterDashboardMedico(Usuario usuario, LocalDate dataReferencia) {
        var medico = medicoRepository.findByUsuario(usuario)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Usuário não possui cadastro de médico"));

        LocalDate dia = dataReferencia != null ? dataReferencia : LocalDate.now();
        LocalDateTime inicio = dia.atStartOfDay();
        LocalDateTime fimExclusivo = dia.plusDays(1).atStartOfDay();
        LocalDateTime agora = LocalDateTime.now();

        List<Consulta> ativasHoje = consultaRepository.findAllAtivasNoIntervaloByMedicoId(medico.getId(), inicio, fimExclusivo);
        long urgenciasHoje = ativasHoje.stream()
                .filter(c -> c.getPrioridade() == PrioridadeConsulta.URGENCIA)
                .count();
        long filaEsperaHoje = ativasHoje.stream()
                .filter(c -> c.getStatus() == StatusConsulta.CHECK_IN)
                .count();
        long emAtendimentoAgora = ativasHoje.stream()
                .filter(c -> c.getStatus() == StatusConsulta.EM_ATENDIMENTO)
                .count();
        long concluidasHoje = ativasHoje.stream()
                .filter(c -> c.getStatus() == StatusConsulta.CONCLUIDA)
                .count();

        long proximosSeteDias = consultaRepository
                .countByMedicoIdAndAtivoTrueAndDataHoraGreaterThanEqualAndDataHoraLessThan(
                        medico.getId(), agora, agora.plusDays(7));
        long totalPacientes = consultaRepository.countPacientesDistintosAtivosByMedicoId(medico.getId());
        long totalConsultas = consultaRepository.countByMedicoIdAndAtivoTrue(medico.getId());
        long totalProntuarios = prontuarioRepository.countByAtivoTrueAndMedicoId(medico.getId());

        List<DadosListagemConsulta> agendaHoje = ativasHoje.stream()
                .map(DadosListagemConsulta::new)
                .toList();

        return new DadosDashboardMedico(
                dia,
                ativasHoje.size(),
                urgenciasHoje,
                filaEsperaHoje,
                emAtendimentoAgora,
                concluidasHoje,
                proximosSeteDias,
                totalPacientes,
                totalConsultas,
                totalProntuarios,
                agendaHoje
        );
    }

    @Transactional(readOnly = true)
    public DadosDashboardGestor obterDashboardGestor(int dias) {
        if (dias < 1 || dias > 365) {
            throw new ValidacaoException("O período deve estar entre 1 e 365 dias.");
        }

        LocalDate hoje = LocalDate.now();
        LocalDateTime inicioHoje = hoje.atStartOfDay();
        LocalDateTime fimExclusivo = hoje.plusDays(1).atStartOfDay();
        LocalDateTime inicioPeriodo = hoje.minusDays(dias - 1L).atStartOfDay();

        Map<StatusConsulta, Long> statusHoje = mapearContagemPorStatus(
                consultaRepository.countByStatusNoIntervalo(inicioHoje, fimExclusivo)
        );
        long consultasHoje = statusHoje.entrySet().stream()
                .filter(e -> e.getKey().isAtivaNaAgenda())
                .mapToLong(Map.Entry::getValue)
                .sum();

        Map<StatusConsulta, Long> statusPeriodo = mapearContagemPorStatus(
                consultaRepository.countByStatusNoIntervalo(inicioPeriodo, fimExclusivo)
        );
        long totalPeriodo = statusPeriodo.values().stream().mapToLong(Long::longValue).sum();
        long concluidasPeriodo = statusPeriodo.getOrDefault(StatusConsulta.CONCLUIDA, 0L);
        long canceladasPeriodo = statusPeriodo.getOrDefault(StatusConsulta.CANCELADA, 0L);
        long ausentesPeriodo = statusPeriodo.getOrDefault(StatusConsulta.AUSENTE, 0L);
        long reagendadasPeriodo = statusPeriodo.getOrDefault(StatusConsulta.REAGENDADA, 0L);

        double taxaCancelamento = calcularPercentual(canceladasPeriodo, totalPeriodo);
        double taxaAbsenteismo = calcularPercentual(ausentesPeriodo, totalPeriodo);

        List<ContagemStatusDashboard> porStatus = Arrays.stream(StatusConsulta.values())
                .map(st -> new ContagemStatusDashboard(st, statusPeriodo.getOrDefault(st, 0L)))
                .toList();

        List<ProducaoEspecialidadeDashboard> porEspecialidade = consultaRepository
                .countProducaoPorEspecialidadeNoIntervalo(inicioPeriodo, fimExclusivo)
                .stream()
                .map(row -> new ProducaoEspecialidadeDashboard(
                        String.valueOf(row[0]),
                        toLong(row[1]),
                        toLong(row[2]),
                        toLong(row[3]),
                        toLong(row[4])
                ))
                .toList();

        return new DadosDashboardGestor(
                dias,
                consultasHoje,
                totalPeriodo,
                concluidasPeriodo,
                canceladasPeriodo,
                ausentesPeriodo,
                reagendadasPeriodo,
                taxaCancelamento,
                taxaAbsenteismo,
                medicoRepository.countByAtivoTrue(),
                pacienteRepository.countByAtivoTrue(),
                porStatus,
                porEspecialidade
        );
    }

    private Map<StatusConsulta, Long> mapearContagemPorStatus(List<Object[]> rows) {
        Map<StatusConsulta, Long> mapa = new EnumMap<>(StatusConsulta.class);
        for (Object[] row : rows) {
            if (row[0] instanceof StatusConsulta status) {
                mapa.put(status, toLong(row[1]));
            }
        }
        return mapa;
    }

    private long toLong(Object value) {
        return value instanceof Number n ? n.longValue() : 0L;
    }

    private double calcularPercentual(long parte, long total) {
        if (total <= 0) {
            return 0.0;
        }
        return Math.round((parte * 1000.0) / total) / 10.0;
    }
}
