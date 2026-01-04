# Job Engine

Engine de jobs com 3 modos de execução para estudo de concorrência na JVM.

## Executar

```bash
./gradlew bootRun
```

Ou com stress test automático:

```bash
./scripts/stress-test.sh 100
```

## Modos de Execução

| Modo | Estratégia | Melhor para |
|------|------------|-------------|
| **SEQUENTIAL** | 1 thread, jobs em série | Debug, ordenação |
| **THREAD_POOL** | Pool de N threads paralelas | CPU-bound (cálculos) |
| **ASYNC** | Virtual Threads (milhões) | I/O-bound (espera) |

**CPU-bound** = usa processador intensamente (cálculos, criptografia)  
**I/O-bound** = espera algo externo (rede, banco, arquivos)

### Qual modo usar?

```
Seu trabalho é mais:
        │
   ┌────┴────┐
   ▼         ▼
Cálculo    Espera
(CPU)      (I/O)
   │         │
   ▼         ├── Poucas tarefas → THREAD_POOL
THREAD_POOL │
             └── Muitas tarefas → ASYNC
```

## API

```bash
# Criar job
curl -X POST http://localhost:8080/api/jobs \
  -H "Content-Type: application/json" \
  -d '{"name": "meu-job", "payload": "dados", "executionMode": "ASYNC"}'

# Ver métricas
curl http://localhost:8080/api/metrics/compare

# Resetar métricas
curl -X DELETE http://localhost:8080/api/metrics
```

| Método | Endpoint | Descrição |
|--------|----------|-----------|
| POST | `/api/jobs` | Criar job |
| GET | `/api/jobs` | Listar jobs |
| GET | `/api/metrics/compare` | Comparar modos |
| DELETE | `/api/metrics` | Zerar métricas |

## Stress Test

```bash
./scripts/stress-test.sh 100   # 100 jobs por modo
./scripts/stress-test.sh 500   # 500 jobs por modo
```

O script inicia a API automaticamente, reseta métricas, submete jobs e exibe resultados.

## Resiliência

O sistema usa **Resilience4j** para lidar com falhas:

| Padrão | Comportamento |
|--------|---------------|
| **Retry** | Tenta 3x com backoff exponencial (500ms → 1s → 2s) |
| **Fallback** | Após 3 falhas, retorna erro claro |

```
Job falhou (10% chance)
       │
       ▼
  Retry 1 (espera 500ms)
       │ falhou
       ▼
  Retry 2 (espera 1s)
       │ falhou
       ▼
  Retry 3 (espera 2s)
       │ falhou
       ▼
  Fallback → Job marcado FAILED
```

**Impacto:** `failedCount` cai de ~10% para ~1%.

## Configuração

Edite `src/main/resources/application.yml`:

```yaml
job-engine:
  thread-pool:
    core-size: 12     # Threads ativas (= CPUs)
    max-size: 24      # Máximo sob carga
  io-simulation:
    min-latency-ms: 200
    max-latency-ms: 400
    # Chaos testing (simula falhas reais)
    failure-rate: 0.10        # 10% chance de falha
    timeout-rate: 0.05        # 5% chance de latência extrema
    timeout-latency-ms: 5000  # 5s quando timeout

resilience4j:
  retry:
    instances:
      ioSimulator:
        max-attempts: 3
        wait-duration: 500ms
        exponential-backoff-multiplier: 2
```

Para desativar chaos, use `failure-rate: 0` e `timeout-rate: 0`.

## Documentação

| Documento | Conteúdo |
|-----------|----------|
| [Jornada de um Job](docs/JORNADA-JOB.md) | Fluxo interno do sistema |
| [Trade-offs](docs/TRADEOFFS.md) | Análise técnica profunda de cada modo |

## Stack

Java 21 · Spring Boot 3.4 · Micrometer · Gradle
