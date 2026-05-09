CREATE SCHEMA atl;

CREATE TABLE atl.estadocivil (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    mnemonico VARCHAR(20) NOT NULL,
    nome VARCHAR(100) NOT NULL,
    descricao VARCHAR(255),
    CONSTRAINT chk_estadocivil_mnem CHECK (char_length(mnemonico) >= 1)
);
CREATE UNIQUE INDEX uk_estadocivil_mnemonico ON atl.estadocivil(mnemonico);
COMMENT ON TABLE atl.estadocivil IS 'Cadastro de estado civil';
COMMENT ON COLUMN atl.estadocivil.mnemonico IS 'Codigo curto';

CREATE TABLE atl.cliente (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id INTEGER NOT NULL DEFAULT 1,
    cpf VARCHAR(14) NOT NULL,
    nome VARCHAR(200) NOT NULL,
    fk_estadocivil_id BIGINT REFERENCES atl.estadocivil(id),
    foto BYTEA,
    datanascimento DATE,
    sexo CHAR(1),
    CONSTRAINT uk_cliente_cpf UNIQUE (cpf),
    CONSTRAINT chk_cliente_sexo CHECK (sexo IN ('M','F','O'))
);
CREATE INDEX idx_cliente_cpf ON atl.cliente(cpf);
COMMENT ON TABLE atl.cliente IS 'Cadastro de pacientes';

CREATE VIEW atl.cliente_vw AS SELECT id, nome FROM atl.cliente;
