# Resumen “desde cero” — Proyecto multi–módulo Maven en NetBeans

 **Estructura semi-profesional** (más allá de “solo `src`/`Source Packages`”) para el proyecto **Shibasito** con RabbitMQ, alineada con 

 **3+ nodos**, **BD1 ≠ BD2**, **LP1 ≠ LP2 ≠ LP3**, **validación en RENIEC antes del banco**, **sin websockets**, **script de desempeño (≥1000) con hilos**, y **entregables (código, scripts, informe, presentación)**. 

---

# Estructura recomendada (monorepo)

```
shibasito/
├─ README.md
├─ LICENSE            # opcional
├─ .env.example       # variables de entorno (RabbitMQ, DBs)
├─ docs/              # informe, presentación, evidencias (capturas UI, métricas)
│  ├─ informe.pdf
│  ├─ presentacion.pdf
│  └─ evidencias/
├─ diagrams/          # arquitectura y protocolo (Mermaid/PlantUML)
│  ├─ arquitectura.mmd
│  └─ protocolo.mmd
├─ infra/
│  ├─ rabbitmq/
│  │  ├─ definitions.json   # opcional: exchanges/colas predefinidas
│  │  └─ policies.json
│  └─ sql/
│     ├─ bd1_postgres.sql   # Banco: cuentas, prestamos, transacciones
│     └─ bd2_mysql.sql      # RENIEC: personas
├─ java/                    # Proyecto Maven padre (NetBeans-friendly)
│  ├─ pom.xml               # packaging: pom
│  ├─ common/               # utilidades compartidas (DTO, JSON, Rabbit, config)
│  │  └─ pom.xml
│  ├─ bank-service/         # LP1 (Java): servicio del Banco + BD1 (PostgreSQL)
│  │  ├─ pom.xml
│  │  └─ src/main/{java,resources}
│  ├─ desktop-client/       # LP3 (Java/JavaFX): app de escritorio (sin QR)
│  │  ├─ pom.xml
│  │  └─ src/main/{java,resources}
│  └─ loadgen/              # script de desempeño (≥1000, hilos)
│     ├─ pom.xml
│     └─ src/main/{java,resources}
└─ python/                  # LP2 (Python): servicio RENIEC + BD2 (MySQL/MariaDB)
   └─ reniec-service/
      ├─ app.py
      ├─ requirements.txt
      └─ .env
```

**Por qué así (resumen):**

* **Separación por responsabilidad**: `bank-service` (Java) y `reniec-service` (Python) viven independientes, y el **desktop** también (LPs diferentes en nodos independientes). 
* **`common/`** centraliza DTO/serialización/config, evitando duplicación.
* **`infra/`** agrupa SQL y definiciones de colas para reproducir entornos.
* **`docs/` + `diagrams/`** garantizan los entregables: arquitectura, protocolo, explicación y capturas. 
* **`loadgen/`** cumple la prueba de desempeño con hilos (≥1000). 

---

# Qué va dentro (concreto y corto)

## `infra/sql/`

* **`bd1_postgres.sql`**: `Cuentas`, `Prestamos`, `Transacciones` + semillas para demo. (BD1 del Banco). 
* **`bd2_mysql.sql`**: `Personas` (RENIEC) + semillas. 

## `java/pom.xml` (padre Maven)

* `packaging = pom`, define **versión Java** y dependencias comunes (RabbitMQ client, JDBC, Jackson, JavaFX), y lista los **modules**: `common`, `bank-service`, `desktop-client`, `loadgen`.
* Ventaja: NetBeans abre el **padre** y detecta automáticamente los módulos.

## `java/common/`

* **DTOs** (p.ej. `BankCmd`, `BankEvt`), **Json** (Jackson), **Rabbit** (factory de conexiones), **Config** (lee `.json`/env).
* Reutilizado por `bank-service`, `desktop-client`, `loadgen`.

## `java/bank-service/` (LP1 – Java)

* **Consume** comandos desde `bank.cmd` → **aplica transacciones** en **PostgreSQL** (BD1) → **publica eventos** en `bank.evt`.
* **Idempotencia**: tabla `inbox(message_id)` para no aplicar dos veces.
* **Sin websockets**: sólo AMQP (RabbitMQ), como exige el enunciado. 

## `python/reniec-service/` (LP2 – Python)

* **RPC** por cola `q.reniec.rpc`: el cliente envía `{dni}`, RENIEC responde datos/validez desde BD2 (MySQL/MariaDB).
* **Obligatorio validar en RENIEC antes de Banco** (flujo). 

## `java/desktop-client/` (LP3 – Java/JavaFX)

* GUI de escritorio **sin QR** (el QR queda para el cliente móvil). 
* Flujo: Login/Validación → Publica comandos → Escucha confirmaciones.

## `java/loadgen/`

* **ExecutorService** con N hilos, envía **≥1000** operaciones (depósitos/transferencias/solicitudes), mide **tps** y **latencias**. 

---



## 1) ¿Cómo se creo PC3-ProConcu-shibasito?

Montamos un **monorepo** con un **proyecto Maven padre** (empaquetado `pom`) y **cuatro módulos** (`common`, `bank-service`, `desktop-client`, `loadgen`).
Ventajas:

* **Un solo build** (compilas el padre y se construye todo en orden).
* **Herencia de configuración** (versiones/plugins centralizados).
* **Aislamiento** (cada módulo tiene sus dependencias y ciclo de vida).
* **NetBeans** muestra todo bajo `Modules` y permite ejecutar/depurar cada hijo.

Estructura esperada en disco (simplificada):

```
PC3-ProConcu-shibasito/
├─ pom.xml                # POM padre (packaging: pom)
├─ common/                # hijo: jar
│  └─ pom.xml
├─ bank-service/          # hijo: jar (servicio Banco)
│  └─ pom.xml
├─ desktop-client/        # hijo: jar (cliente escritorio/JavaFX)
│  └─ pom.xml
└─ loadgen/               # hijo: jar (generador de carga)
   └─ pom.xml
```

---

## 2) Creación en NetBeans (paso a paso)

### 2.1 Crear el **padre**

1. **File → New Project → Java with Maven → POM Project**.
2. `GroupId: a` (o el que prefieras), `ArtifactId: PC3-ProConcu-shibasito`, `Version: 1.0-SNAPSHOT`.
3. Finish.
4. Abre el `pom.xml` y confirma: `<packaging>pom</packaging>` y `<modules>…</modules>` como el que pegaste (ya lo tienes bien).

### 2.2 Crear cada **módulo hijo**

> Dos caminos válidos. Tu NetBeans funciona perfecto con “Create New Module”:

**Opción recomendada (la que te funcionó):**

* Click derecho sobre **`Modules` (nodo del proyecto padre)** → **Create New Module**.
* En el asistente, elige **Java with Maven → Java Application**.
* **Desmarca** “Create Main Class” para `common`. Para `bank-service`, `desktop-client` y `loadgen` puedes dejarlo marcado (útil para pruebas).
* **Project Location:** la **carpeta del padre** (`.../PC3-ProConcu-shibasito`).
* **Parent/Set Parent**: si aparece la opción, selecciona el **pom del padre**.
* Nombres: crea **cuatro** módulos: `common`, `bank-service`, `desktop-client`, `loadgen`.
* Repite hasta tener los cuatro.

**Opción alternativa (si no apareciera Create New Module):**

* **File → New Project → Java with Maven → Java Application** (una vez por módulo).
* Ubicación: carpeta del **padre**.
* Luego, **abre `pom.xml` del hijo** y añade el bloque `<parent>` (ver §3).

### 2.3 Recargar y compilar

* Click derecho **padre** → **Reload Project** (o “Reload POMs”).
* Click derecho **padre** → **Clean and Build**.
  Deberías ver a `common`, `bank-service`, `desktop-client`, `loadgen` construirse en cadena.

---

## 3) POM mínimo de cada **hijo**

Abre el `pom.xml` de **cada** módulo y confirma/añade:

### Plantilla base (copia y cambia `artifactId`)

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <parent>
    <groupId>a</groupId>
    <artifactId>PC3-ProConcu-shibasito</artifactId>
    <version>1.0-SNAPSHOT</version>
    <relativePath>../pom.xml</relativePath>
  </parent>

  <artifactId>common</artifactId> <!-- cámbialo a bank-service / desktop-client / loadgen -->
  <packaging>jar</packaging>
</project>
```

> Con ese bloque `<parent>` el hijo **hereda** propiedades y queda ligado al padre.
> En el **padre**, el bloque `<modules>` ya lista los cuatro (como en tu POM).

---

## 4) Dudas que surgieron (y respuestas)

**❓ New → Other… (sobre el proyecto) no muestra “Maven”.**
Ese asistente es para **archivos** (Dockerfile, YAML, etc.), no para **proyectos/módulos**.
➡️ Usa **Create New Module** (en el nodo `Modules`) o **New Project**.

**❓ “Project from Archetype” vs “Java Application”.**
Un **archetype** es una **plantilla**. “Project from Archetype” te deja elegir plantillas (JavaFX, Micronaut, etc.).

* Si quieres un módulo JavaFX ya armado, elige el **archetype JavaFX** para `desktop-client`.
* Si quieres un JAR vacío, **Java Application** es suficiente.

**❓ “Project with Existing POM” sirve para este caso?**
Solo cuando **importas** un proyecto **que ya existe en disco** con su `pom.xml`. No crea módulo nuevo; evita usarlo si partes desde cero.

**❓ Por qué `common` aparece “dos veces” (bajo `Modules` y como proyecto aparte).**
NetBeans muestra:

* el **árbol lógico** del padre (nodo `Modules`), y
* los **proyectos abiertos** en el workspace.
  Puedes cerrar el proyecto `common` “suelto” si quieres; seguirá listado bajo `Modules`.

**❓ Paquetes como `a.common` o `cc4p1.common` ¿importa?**
Funciona igual. Aconsejable usar un **namespace estable** (p. ej. `a.common` o `cc4p1.common`) y mantener la convención en todos los módulos.

---

## 5) Buenas prácticas mínimas por módulo

* **`common`**: DTOs, utilitarios (JSON, RabbitMQ, config). Sin `main`.
* **`bank-service`**: `main` que arranca el consumidor/procesador; dependerá de `common`.
* **`desktop-client`**: `main` (CLI o JavaFX). También depende de `common`.
* **`loadgen`**: `main` con `ExecutorService`/hilos para pruebas de carga; depende de `common`.

En los hijos que lo necesiten, agrega en su `pom.xml`:

```xml
<dependencies>
  <dependency>
    <groupId>a</groupId>
    <artifactId>common</artifactId>
    <version>${project.version}</version>
  </dependency>
  <!-- otras dependencias propias del módulo -->
</dependencies>
```

---

**Resumen de cambios** que aplique en pom.xml de **common** y por qué son correctos: * **Se quito el main de common** (la clase a.common.Common con public static void main). * Efecto: el módulo **ya no intenta ejecutarse**; queda como **librería**. * **Eliminaste la referencia a exec.mainClass**:
```xml
  <!-- removido -->
  <exec.mainClass>a.common.Common</exec.mainClass>
  ```
* Efecto: Maven ya **no** tratará de correr common con exec:java. Evitas confusión y errores. 
* **(Recomendado y ya alineado)** packaging permanece en jar. 
  * Efecto: confirma que common es un **JAR reusable**. 
* **(Si lo moviste)** Centralizaste la versión de Java en el **padre** (<maven.compiler.release> en el POM raíz) y **la quitaste** del pom de common. 
* Efecto: **todos los módulos** compilan con la misma versión; menos drift y problemas de compatibilidad. 

## Cómo quedó (mínimo esperado)
```xml
<project ...>
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>a</groupId>
    <artifactId>PC3-ProConcu-shibasito</artifactId>
    <version>1.0-SNAPSHOT</version>
    <relativePath>../pom.xml</relativePath>
  </parent>

  <artifactId>common</artifactId>
  <packaging>jar</packaging>
  <!-- sin exec.mainClass, sin main -->
</project>
```

## Por qué está bien hacerlo así 
* **Responsabilidad única**: common es **solo utilidades/DTOs**; no se ejecuta por sí mismo. 
* **Build limpio**: el padre compila common primero y luego los módulos ejecutables (bank-service, desktop-client, loadgen) que **dependen** de él. 
* **Menos ruido**: no más “Run” accidental sobre common; evitas errores tipo “no hay clase principal”.   

-> common ahora se **instala** en tu repo local (~/.m2/...) como librería. * Los demás módulos pueden declararlo:




# Resumen del scaffolding (estructura base del proyecto (parte java) )

* **Monorepo Maven multimódulo**: `common/`, `bank-service/`, `desktop-client/`, `loadgen/`.
* **Padre (`packaging: pom`)**: define Java `release 24` y orquesta los módulos.
* **common/**: utilitarios compartidos (`Config`, `Rabbit`) + dependencias **Jackson** (`jackson-databind`) y **RabbitMQ** (`amqp-client`).
* **bank-service/**: servicio que consume de `bank.cmd` y (por ahora) imprime el JSON recibido.
* **desktop-client/**: cliente que publica un `deposit` a `bank.cmd` (CLI mínimo).
* **loadgen/**: shell vacío listo para el generador de carga.
* **Configs externos** por módulo (`config/*.json`) y paso de ruta vía env var `CFG`.

---

# Cómo correr (runbook mínimo y reproducible)

1. **RabbitMQ** arriba y accesible (usuario `admin`, vhost `aaa`).
2. Crear configs desde tus `.example` y ajustar credenciales si hace falta:

   * `bank-service/config/bank.json`
   * `desktop-client/config/desktop.json`
3. **Levantar bank-service**:

   ```bash
   cd bank-service
   mvn exec:java -Denv.CFG=config/bank.json
   ```

   Verás: `BankService up. Waiting messages...`
4. **Enviar un depósito desde desktop-client** (otra terminal):

   ```bash
   cd desktop-client
   mvn exec:java -Denv.CFG=config/desktop.json
   ```

   Verás en desktop: `enviado deposit, corrId=...`
   y en bank-service: `[bank] recibido: {...}`

Con eso compruebas AMQP end-to-end y el wiring entre módulos.

---

# Problemas importantes que aparecieron y sus soluciones

1. **`package com.fasterxml.jackson.databind does not exist`**

   * **Causa**: faltaba la dependencia **Jackson** en el módulo que usa `ObjectMapper`.
   * **Solución**: agregar en `common/pom.xml`:

     ```xml
     <dependency>
       <groupId>com.fasterxml.jackson.core</groupId>
       <artifactId>jackson-databind</artifactId>
       <version>2.17.2</version>
     </dependency>
     ```

     Recargar proyecto y `mvn clean package`.

2. **Resultados “raros” en el buscador de dependencias de NetBeans** (aparecían `com.jwebmp.*` o `org.wso2.*`)

   * **Causa**: NetBeans muestra artefactos “sombreados” que no son los oficiales.
   * **Solución**: elegir **exactamente** `com.fasterxml.jackson.core : jackson-databind` (o editar el POM a mano).

3. **`package com.rabbitmq.client does not exist`**

   * **Causa**: el módulo con código RabbitMQ no tenía **amqp-client** en el classpath.
   * **Solución**: como `Rabbit.java` vive en **common**, agregar allí:

     ```xml
     <dependency>
       <groupId>com.rabbitmq</groupId>
       <artifactId>amqp-client</artifactId>
       <version>5.20.0</version>
     </dependency>
     ```

4. **`cannot find symbol a.common.Config / Rabbit` en bank-service**

   * **Causa**: `bank-service` no **dependía** del módulo `common`.
   * **Solución**: añadir en `bank-service/pom.xml`:

     ```xml
     <dependency>
       <groupId>a</groupId>
       <artifactId>common</artifactId>
       <version>${project.version}</version>
     </dependency>
     ```

5. **Padre sin clase `main` (no se “ejecuta” el padre)**

   * **Causa**: el POM del padre tiene `packaging: pom` (correcto) — solo orquesta.
   * **Solución**: ejecutar **cada hijo**. Para facilitarlo, añadir `exec-maven-plugin` con `exec.mainClass` en `bank-service` y `desktop-client`.

6. **Warning SLF4J: “Defaulting to no-operation (NOP) logger implementation”**

   * **Causa**: no hay implementación SLF4J enlazada.
   * **Solución** (opcional): agregar

     ```xml
     <dependency>
       <groupId>org.slf4j</groupId>
       <artifactId>slf4j-simple</artifactId>
       <version>2.0.13</version>
     </dependency>
     ```

     o simplemente ignorar (no afecta la ejecución).

7. **Warnings `sun.misc.Unsafe` al ejecutar**

   * **Causa**: tooling interno de NetBeans/Maven (Guice) con APIs marcadas “deprecated”.
   * **Solución**: inofensivo; ignorar. No impacta tu app.

8. **`uses unchecked or unsafe operations` en `Config`**

   * **Causa**: parseo con `Map` sin genéricos.
   * **Solución rápida**: dejarlo así (solo warning).
   * **Solución limpia (posterior)**: usar un POJO `Config` y `readValue(path, Config.class)` o `JsonNode` con `readTree()`.

9. **Cambios de POM que no se reflejan**

   * **Causa**: POMs no recargados o Maven en modo offline.
   * **Solución**: desactivar “Work Offline” y hacer:

     * en NetBeans: **Reload Project** del padre + **Clean and Build**;
     * o en terminal: `mvn -U -pl bank-service -am clean package`.

10. **¿Por qué no hay `src` en el padre?**

    * **Causa**: diseño intencional. El padre **no contiene código**; solo módulos.
    * **Solución**: mantenerlo así; los `main` viven en los hijos.

---

# Lecciones aprendidas (lo clave a recordar)

* En **multimódulo**, cualquier clase compartida va a **common** y los demás **declaran dependencia** a `common`.
* Las dependencias “de bajo nivel” (como `amqp-client`) conviene ponerlas **donde se usan** (en este caso, `common` porque `Rabbit.java` está ahí).
* El **padre** no corre; los **hijos** sí. Usa `exec-maven-plugin` y `CFG=config/*.json` para pasar configuraciones externas.
* Cuando falte una clase de un paquete (`com.fasterxml.jackson…` / `com.rabbitmq…`), piensa **dependencia faltante** o **módulo que no depende del otro**.
* “Reload Project” + “Clean and Build” suelen resolver el 90% de los falsos rojos del IDE.

---


