# PC3 – ProConcu – Shibasito

Monorepo Maven (parent + módulos): `common/`, `bank-service/`, `desktop-client/`, `loadgen/`.

## Correr (Ubuntu)
1) RabbitMQ + vhost `aaa` + user `admin/admin`.
2) Postgres `bankdb` + user `bank/bank` (cuando activemos BD).
3) Crear configs desde los `.example`.

### Bank
```bash
cd bank-service
mvn exec:java -Denv.CFG=config/bank.json
