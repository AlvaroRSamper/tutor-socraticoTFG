ALTER TABLE perfil_alumno_registro ADD COLUMN ultimo_dia_activo date;
ALTER TABLE perfil_alumno_registro ADD COLUMN racha_actual integer not null default 0;
ALTER TABLE perfil_alumno_registro ADD COLUMN racha_maxima integer not null default 0;
