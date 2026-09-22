ALTER TABLE perfil_alumno_registro ADD COLUMN nivel_andamiaje integer;
ALTER TABLE perfil_alumno_registro ADD COLUMN turnos_atascado integer;
ALTER TABLE perfil_alumno_registro ADD COLUMN turnos_sueltos integer;
ALTER TABLE perfil_alumno_registro ADD COLUMN ultima_consulta_calado bigint;
ALTER TABLE registro_consulta ADD COLUMN nivel_revelado integer;
