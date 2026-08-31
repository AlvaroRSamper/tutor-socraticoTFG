
alter table if exists registro_resolucion
    add column if not exists porcentaje_autoria integer;

alter table if exists registro_resolucion
    add column if not exists codigo_tutor_acumulado text;

alter table if exists estado_microhito
    add column if not exists nivel_ayuda_max integer default 0;

alter table if exists estado_microhito
    add column if not exists n_ayudas integer default 0;
