ALTER TABLE perfil_alumno_registro ADD COLUMN consentimiento_datos boolean not null default false;
ALTER TABLE perfil_alumno_registro ADD COLUMN fecha_consentimiento timestamp;
