# PC3 – ProConcu – **Shibasito**

> Proyecto didáctico para **Programación Paralela, Concurrente y Distribuida (CC4P1)**.
> Demuestra **productor–consumidor** con **RabbitMQ**, **RPC** con *reply-to/correlationId*, **idempotencia** de comandos, y (luego) persistencia **ACID** en **PostgreSQL**.

---

## 📌 Objetivo del proyecto

Construir un **mini-sistema bancario distribuido** con servicios que se comunican **asíncronamente** vía **RabbitMQ**:

* **desktop-client** (Java/JavaFX o CLI): valida DNI en **RENIEC** por **RPC** y publica comandos bancarios (deposit, withdraw, transfer).
* **bank-service** (Java): consume comandos, aplica **transacciones ACID** (PostgreSQL) e **informa resultados** por evento.
* **reniec-service** (Python): atiende RPC sobre una cola (*q.reniec.rpc*) consultando **MySQL/MariaDB**.
* **loadgen** (Java): genera **carga concurrente** (≥1000 operaciones) y emite métricas (throughput, latencias p50/p95).

El proyecto **no busca** ser un core bancario real, sino un **laboratorio** para practicar **colas**, **concurrencia**, **reintentos**, **idempotencia** y **consistencia**.

---

## 🧩 Módulos (monorepo Maven)

```
PC3-ProConcu-shibasito/
├─ pom.xml                     # POM padre (packaging: pom)
├─ common/                    # utilidades compartidas (Config, Rabbit, DTOs)
├─ bank-service/              # servicio Banco (consumidor AMQP + JDBC)
├─ desktop-client/            # cliente (CLI/JavaFX) productor AMQP + RPC RENIEC
└─ loadgen/                   # generador de carga concurrente
```

### common/

* `Config` → lee `config/*.json` (ruta por env var `CFG`).
* `Rabbit` → crea conexiones/canales AMQP con *automatic recovery*.
* (opcional) **DTOs** (`BankCmd`, `BankEvt`) y utilidades JSON.

### bank-service/

* Declara `bank.cmd` (exchange **direct**), **q.bank.cmd** (durable).
* Consume con **ack manual** y **prefetch** (QoS).
* (Luego) Aplica **ACID + idempotencia** (tabla `inbox(message_id PK)`).
* Publica eventos a `bank.evt` (fanout/direct) → **q.bank.reply**.

### desktop-client/

* **RPC RENIEC**: envía a **q.reniec.rpc** con `replyTo` temporal y `correlationId`.
* Publica comandos a `bank.cmd`.
* (Luego) Se suscribe a **q.bank.reply** y refleja resultados en la UI.

### loadgen/

* `ExecutorService` con M×K mensajes → mide **ops/s** y latencias **p50/p95** → `out/report.csv`.

---

## 🏗️ Arquitectura (visión)

```mermaid
flowchart LR
  subgraph Client[desktop-client (Java/JavaFX)]
    A1[RPC DNI -> q.reniec.rpc]
    A2[Publish BankCmd -> bank.cmd]
    A3[Consume BankEvt <- q.bank.reply]
  end

  subgraph MQ[RabbitMQ]
    E1[exchange bank.cmd (direct)]
    Q1[q.bank.cmd]
    E2[exchange bank.evt (fanout)]
    Q2[q.bank.reply]
    QRPC[q.reniec.rpc]
  end

  subgraph Bank[bank-service (Java)]
    B1[Consume q.bank.cmd]
    B2[(PostgreSQL)]
    B3[Publish bank.evt]
  end

  subgraph Reniec[reniec-service (Python)]
    R1[Consume q.reniec.rpc]
    R2[(MySQL/MariaDB)]
  end

  A1 --> MQ
  A2 --> E1 --> Q1 --> B1 --> B2 --> B3 --> E2 --> Q2 --> A3
  QRPC --> R1 --> R2 --> A1
```

---

## 📨 Contratos de mensajería

### RPC RENIEC

**Request**

```json
{ "dni": "01234567" }
```

**Response OK**

```json
{ "ok": true, "dni": "01234567", "nombres": "ALAN", "apellidos": "ESPINOZA", "f_nac": "2000-01-02" }
```

**Response NO OK**

```json
{ "ok": false, "error": "DNI_NO_ENCONTRADO" }
```

### Comandos Banco → `bank.cmd`

```json
{
  "messageId": "uuid-...",
  "type": "deposit | withdraw | transfer | loan_apply",
  "actorDni": "01234567",
  "payload": { "accountId": "A-001", "amount": 150.0, "destAccountId": "A-999" }
}
```

### Eventos/Respuestas Banco → `bank.evt`

```json
{
  "correlationId": "uuid-...",
  "ok": true,
  "type": "deposit_ok | withdraw_ok | ...",
  "result": { "accountId": "A-001", "newBalance": 350.0 },
  "error": null
}
```

---

## 🗃️ Esquema mínimo (PostgreSQL, banco)

```sql
create table if not exists cuentas(
  id_cuenta text primary key,
  saldo     numeric not null default 0
);
create table if not exists inbox(
  message_id text primary key      -- para idempotencia
);
insert into cuentas(id_cuenta, saldo) values ('A-001', 200) on conflict do nothing;
```

> **Idempotencia**: cada comando se registra en `inbox` (PK). Si RabbitMQ reentrega, el `INSERT` falla por PK y **evita aplicar dos veces**.

---

## 🔧 Requisitos

* **JDK** 24 (o 21 si ajustas `<java.release>` en el POM padre).
* **Maven** 3.9+.
* **RabbitMQ** (con *management plugin*).
* **PostgreSQL 16** (banco).
* **Python 3.11** + **pika** (cuando agregues `reniec-service`).
* **MySQL/MariaDB** (RENIEC real).

---

## ⚙️ Configuración (archivos externos)

> **No subas** credenciales reales. Versiona archivos **`.example`** y cada developer copia el suyo.

`bank-service/config/bank.json.example`

```json
{
  "mqHost": "127.0.0.1",
  "mqVhost": "aaa",
  "mqUser": "admin",
  "mqPass": "admin",
  "bankCmdEx": "bank.cmd",
  "bankEvtEx": "bank.evt",
  "reniecRpcQueue": "q.reniec.rpc",
  "clientReplyQueue": "q.bank.reply",
  "pgUrl": "jdbc:postgresql://127.0.0.1:5432/bankdb",
  "pgUser": "bank",
  "pgPass": "bank"
}
```

`desktop-client/config/desktop.json.example`

```json
{
  "mqHost": "127.0.0.1",
  "mqVhost": "aaa",
  "mqUser": "admin",
  "mqPass": "admin",
  "bankCmdEx": "bank.cmd",
  "bankEvtEx": "bank.evt",
  "reniecRpcQueue": "q.reniec.rpc",
  "clientReplyQueue": "q.bank.reply"
}
```

**Cómo usa el proyecto la configuración**
Cada *main* lee la ruta desde la variable de entorno **`CFG`**:

```
mvn exec:java -Denv.CFG=config/bank.json
```

---

## ▶️ Ejecución (TL;DR)

1. **RabbitMQ**: usuario/vhost

   ```bash
   # una sola vez
   sudo rabbitmq-plugins enable rabbitmq_management
   sudo rabbitmqctl add_user admin admin
   sudo rabbitmqctl add_vhost aaa
   sudo rabbitmqctl set_permissions -p aaa admin ".*" ".*" ".*"
   sudo rabbitmqctl set_user_tags admin administrator
   ```

   UI: `http://localhost:15672` (admin/admin)

2. **PostgreSQL** (banco)

   ```bash
   sudo -u postgres psql -c "create user bank with password 'bank';"
   sudo -u postgres psql -c "create database bankdb owner bank;"
   psql -U bank -d bankdb -h 127.0.0.1 -c "\i infra/sql/bd1_postgres.sql"   # cuando agregues infra/
   ```

3. **Crear configs reales** a partir de `.example`:

   ```
   cp bank-service/config/bank.json.example bank-service/config/bank.json
   cp desktop-client/config/desktop.json.example desktop-client/config/desktop.json
   ```

4. **Levantar servicios** (en terminales separadas):

   ```bash
   # Banco
   cd bank-service
   mvn exec:java -Denv.CFG=config/bank.json
   # → "BankService up. Waiting messages..."

   # Cliente (CLI)
   cd ../desktop-client
   mvn exec:java -Denv.CFG=config/desktop.json
   # → "enviado deposit, corrId=..."
   # y en banco se imprime el JSON recibido
   ```

> **Nota**: por ahora el banco **sólo imprime** el comando recibido; en la siguiente iteración conectará a PostgreSQL y emitirá eventos a `bank.evt`.

---

## 🧠 Concurrencia, fiabilidad y buenas prácticas

* **Durabilidad**: exchanges/colas `durable=true`; mensajes **persistentes** (`deliveryMode=2`) para comandos.
* **ACK manual + Prefetch**: `basicAck` y `basicQos(N)` para *fair dispatch*.
* **Idempotencia**: `inbox(message_id PK)` en banco.
* **RPC robusto**: `replyTo` efímero, `correlationId` único; *timeout* del lado cliente.
* **Reintentos**: `basicNack(requeue=true)` para transitorios; considerar DLX/TTL para *poison messages*.
* **Métricas**: *loadgen* registrará latencias (p50/p95) y throughput.

---

## 🛠️ Problemas típicos y soluciones

* **`package com.fasterxml.jackson.databind does not exist`**
  → Falta `jackson-databind` en el módulo que usa `ObjectMapper` (agrega al POM y recarga).

* **`package com.rabbitmq.client does not exist`**
  → Falta `amqp-client` en el módulo que usa `Rabbit` (o depende de `common` que ya lo trae).

* **`cannot find symbol a.common.Config`**
  → El módulo no depende de `common`. Agrégalo en el POM:

  ```xml
  <dependency>
    <groupId>a</groupId><artifactId>common</artifactId><version>${project.version}</version>
  </dependency>
  ```

* **SLF4J NOP**
  → Es aviso. Si quieres logs simples: `org.slf4j:slf4j-simple:2.0.13`.

* **Warnings `sun.misc.Unsafe`**
  → Provienen del *runner* de Maven/NetBeans: **inofensivos**.

* **No se reflejan cambios de POM**
  → Quita “Maven Offline”, **Reload Project** del padre y `mvn -U clean package`.

---

## 🗺️ Roadmap 

1. **Banco + Postgres**: `deposit/withdraw/transfer` con **ACID** + **idempotencia**.
2. **Eventos**: publicar a `bank.evt`; el cliente **consume** `q.bank.reply`.
3. **RENIEC real** (Python + MySQL/MariaDB) con cache in-process y reintentos.
4. **JavaFX UI**: reemplazar CLI (mismas colas, mejor UX).
5. **LoadGen** con métricas (CSV) y resumen (throughput, p50/p95, tasa de error).
6. (Opcional) **Policies** de RabbitMQ: TTL, DLX, límites de cola; **Docker Compose** para levantar entorno.
