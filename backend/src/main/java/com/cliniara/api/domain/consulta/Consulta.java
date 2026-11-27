package com.cliniara.api.domain.consulta;

import com.cliniara.api.domain.convenio.Convenio;
import com.cliniara.api.domain.medico.Medico;
import com.cliniara.api.domain.paciente.Paciente;
import com.cliniara.api.exception.ValidacaoException;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Table(name = "consultas")
@Entity(name = "Consulta")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class Consulta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "medico_id")
    private Medico medico;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "paciente_id")
    private Paciente paciente;

    private LocalDateTime dataHora;

    private boolean ativo = true;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusConsulta status = StatusConsulta.AGENDADA;

    @Enumerated(EnumType.STRING)
    private MotivoCancelamento motivoCancelamento;

    @Enumerated(EnumType.STRING)
    private PrioridadeConsulta prioridade;

    @Enumerated(EnumType.STRING)
    private TipoConsulta tipo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "consulta_origem_id")
    private Consulta consultaOrigem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "consulta_reagendada_de_id")
    private Consulta consultaReagendadaDe;

    @Enumerated(EnumType.STRING)
    private CanceladoPor canceladoPor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "convenio_id")
    private Convenio convenio;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime criadoEm;

    @LastModifiedDate
    private LocalDateTime atualizadoEm;

    public Consulta(Medico medico, Paciente paciente, LocalDateTime dataHora, PrioridadeConsulta prioridade) {
        this.medico = medico;
        this.paciente = paciente;
        this.dataHora = dataHora;
        this.prioridade = prioridade;
        this.tipo = TipoConsulta.NORMAL;
        this.status = StatusConsulta.AGENDADA;
        this.ativo = true;
    }

    public Consulta(Medico medico, Paciente paciente, LocalDateTime dataHora,
                    PrioridadeConsulta prioridade, Consulta consultaOrigem) {
        this(medico, paciente, dataHora, prioridade);
        this.consultaOrigem = consultaOrigem;
        this.tipo = TipoConsulta.RETORNO;
    }

    public void confirmar() {
        if (this.status != StatusConsulta.AGENDADA) {
            throw new ValidacaoException("Apenas consultas agendadas podem ser confirmadas.");
        }
        this.status = StatusConsulta.CONFIRMADA;
        this.ativo = true;
    }

    public void registrarCheckIn() {
        if (this.status != StatusConsulta.AGENDADA && this.status != StatusConsulta.CONFIRMADA) {
            throw new ValidacaoException("Check-in só pode ser registrado para consultas agendadas ou confirmadas.");
        }
        this.status = StatusConsulta.CHECK_IN;
        this.ativo = true;
    }

    public void iniciarAtendimento() {
        if (this.status != StatusConsulta.CHECK_IN) {
            throw new ValidacaoException("O atendimento só pode ser iniciado após o check-in do paciente na recepção.");
        }
        this.status = StatusConsulta.EM_ATENDIMENTO;
        this.ativo = true;
    }

    public void concluirAtendimento() {
        if (this.status != StatusConsulta.EM_ATENDIMENTO) {
            throw new ValidacaoException("Apenas consultas em atendimento podem ser concluídas.");
        }
        this.status = StatusConsulta.CONCLUIDA;
        this.ativo = true;
    }

    public void cancelar(MotivoCancelamento motivo, CanceladoPor canceladoPor) {
        if (!this.status.permiteCancelamentoOuReagendamento()) {
            throw new ValidacaoException("A consulta no status " + this.status + " não pode ser cancelada.");
        }
        this.status = StatusConsulta.CANCELADA;
        this.ativo = false;
        this.motivoCancelamento = motivo;
        this.canceladoPor = canceladoPor;
    }

    public void registrarAusencia() {
        if (this.status != StatusConsulta.AGENDADA && this.status != StatusConsulta.CONFIRMADA) {
            throw new ValidacaoException("Ausência só pode ser registrada para consultas agendadas ou confirmadas.");
        }
        this.status = StatusConsulta.AUSENTE;
        this.ativo = false;
    }

    public void marcarComoReagendada() {
        if (!this.status.permiteCancelamentoOuReagendamento()) {
            throw new ValidacaoException("A consulta no status " + this.status + " não pode ser reagendada.");
        }
        this.status = StatusConsulta.REAGENDADA;
        this.ativo = false;
    }

    public void setConsultaReagendadaDe(Consulta consultaReagendadaDe) {
        this.consultaReagendadaDe = consultaReagendadaDe;
    }

    public void setConvenio(Convenio convenio) {
        this.convenio = convenio;
    }
}
