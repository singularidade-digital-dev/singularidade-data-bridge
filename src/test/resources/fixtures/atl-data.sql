INSERT INTO atl.estadocivil (mnemonico, nome, descricao) VALUES
  ('S', 'Solteiro', 'Pessoa nao casada'),
  ('C', 'Casado', 'Em uniao civil'),
  ('D', 'Divorciado', 'Apos divorcio'),
  ('V', 'Viuvo', NULL);

INSERT INTO atl.cliente (cpf, nome, fk_estadocivil_id, datanascimento, sexo) VALUES
  ('12345678900', 'FULANO DA SILVA', 1, '1985-04-12', 'M'),
  ('98765432100', 'CICLANA SANTOS', 2, '1990-08-23', 'F'),
  ('11122233344', 'BELTRANO LIMA', 1, '1978-12-01', 'M'),
  ('55566677788', 'DETRANO SOUZA', 3, '1982-06-15', 'O'),
  ('99988877766', 'NAOIDENT BRASIL', NULL, '2000-01-01', 'M');

ANALYZE atl.estadocivil;
ANALYZE atl.cliente;
