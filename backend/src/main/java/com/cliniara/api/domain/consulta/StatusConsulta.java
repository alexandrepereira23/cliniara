package com.cliniara.api.domain.consulta;

public enum StatusConsulta {
    AGENDADA,
    CONFIRMADA,
    CHECK_IN,
    EM_ATENDIMENTO,
    CONCLUIDA,
    CANCELADA,
    AUSENTE,
    REAGENDADA;

    public boolean isAtivaNaAgenda() {
        return this == AGENDADA
                || this == CONFIRMADA
                || this == CHECK_IN
                || this == EM_ATENDIMENTO
                || this == CONCLUIDA;
    }

    public boolean permiteCancelamentoOuReagendamento() {
        return this == AGENDADA || this == CONFIRMADA || this == CHECK_IN;
    }

    public boolean permiteProntuario() {
        return this == EM_ATENDIMENTO || this == CONCLUIDA;
    }
}
