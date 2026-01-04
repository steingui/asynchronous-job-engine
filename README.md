# Job Engine

Engine de jobs com 3 modos de execução para estudo de concorrência na JVM.

## Executar

```bash
# Iniciar API
./gradlew bootRun

# Iniciar monitoramento (Prometheus + Grafana)
cd docker && sudo docker compose up -d
```

## Testar

```bash
# Stress test único (100 jobs por modo)
./scripts/stress-test.sh 100

# Stress test contínuo (a cada 10s, 200 jobs por modo)
./scripts/test-tempo.sh 10 200
```

## Monitorar

| Serviço | URL | Credenciais |
|---------|-----|-------------|
| API | http://localhost:8080/api/metrics/compare | - |
| Prometheus | http://localhost:9090 | - |
| Grafana | http://localhost:3000 | admin/admin |

No Grafana: **Dashboards** → **Job Engine**

## API

```bash
# Criar job
curl -X POST http://localhost:8080/api/jobs \
  -H "Content-Type: application/json" \
  -d '{"name": "meu-job", "payload": "dados", "executionMode": "ASYNC"}'

# Comparar modos
curl http://localhost:8080/api/metrics/compare

# Resetar métricas
curl -X DELETE http://localhost:8080/api/metrics
```

## Configuração

Edite `src/main/resources/application.yml`:

```yaml
job-engine:
  thread-pool:
    core-size: 12     # Threads (= CPUs)
    max-size: 24
  io-simulation:
    min-latency-ms: 200
    max-latency-ms: 400
    failure-rate: 0.10  # 10% falhas (chaos)
    timeout-rate: 0.05  # 5% timeouts
```

## Documentação

| Documento | Conteúdo |
|-----------|----------|
| [Trade-offs](docs/TRADEOFFS.md) | Análise técnica dos 3 modos, resiliência, chaos |
| [Jornada de um Job](docs/JORNADA-JOB.md) | Fluxo interno do sistema |

## Stack

Java 21 · Spring Boot 3.4 · Resilience4j · Micrometer · Prometheus · Grafana
