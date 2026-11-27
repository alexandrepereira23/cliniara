package com.cliniara.api.domain.consulta;

import com.cliniara.api.domain.usuario.Usuario;
import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Table(name = "consulta_historico_status")
@Entity(name = "HistoricoStatusConsulta")
@Getter
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
public class HistoricoStatusConsulta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "consulta_id", nullable = false)
    private Consulta consulta;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_anterior")
    private StatusConsulta statusAnterior;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_novo", nullable = false)
    private StatusConsulta statusNovo;

    @Column(name = "usuario_id")
    private Long usuarioId;

    @Column(name = "usuario_login", length = 100)
    private String usuarioLogin;

    @Column(length = 255)
    private String motivo;

    @Column(name = "alterado_em", nullable = false)
    private LocalDateTime alteradoEm;

    public HistoricoStatusConsulta(
            Consulta consulta,
            StatusConsulta statusAnterior,
            StatusConsulta statusNovo,
            Usuario usuario,
            String motivo
    ) {
        this.consulta = consulta;
        this.statusAnterior = statusAnterior;
        this.statusNovo = statusNovo;
        if (usuario != null) {
            this.usuarioId = usuario.getId() > 0 ? usuario.getId() : null;
            this.usuarioLogin = usuario.getLogin();
        }
        this.motivo = motivo;
        this.alteradoEm = LocalDateTime.now();
    }
}
