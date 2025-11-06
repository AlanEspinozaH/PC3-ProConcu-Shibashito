create table if not exists cuentas(
  id_cuenta text primary key,
  saldo     numeric not null default 0
);

create table if not exists inbox(
  message_id text primary key
);

insert into cuentas(id_cuenta, saldo) values ('A-001', 200) on conflict do nothing;
insert into cuentas(id_cuenta, saldo) values ('A-002', 500) on conflict do nothing;
