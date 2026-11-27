ALTER TABLE consultas
    ADD COLUMN status VARCHAR(25) NOT NULL DEFAULT 'AGENDADA',
    ADD COLUMN consulta_reagendada_de_id BIGINT NULL,
    ADD CONSTRAINT fk_consultas_reagendada_de FOREIGN KEY (consulta_reagendada_de_id) REFERENCES consultas(id),
    ADD INDEX idx_consultas_medico_status_data (medico_id, status, data_hora);

UPDATE consultas
SET status = 'CANCELADA'
WHERE ativo = 0;

UPDATE consultas c
INNER JOIN prontuarios p ON p.consulta_id = c.id
SET c.status = 'CONCLUIDA'
WHERE c.ativo = 1;

CREATE TABLE consulta_historico_status (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    consulta_id BIGINT NOT NULL,
    status_anterior VARCHAR(25) NULL,
    status_novo VARCHAR(25) NOT NULL,
    usuario_id BIGINT NULL,
    usuario_login VARCHAR(100) NULL,
    motivo VARCHAR(255) NULL,
    alterado_em DATETIME NOT NULL,
    CONSTRAINT fk_hist_status_consulta FOREIGN KEY (consulta_id) REFERENCES consultas(id),
    CONSTRAINT fk_hist_status_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id),
    INDEX idx_hist_status_consulta_data (consulta_id, alterado_em)
);
