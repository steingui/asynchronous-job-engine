# Jornada de um Job

O que acontece quando você envia um job.

## Fluxo

```
    POST /api/jobs
    { name: "meu-job", executionMode: "ASYNC" }
            │
            ▼
    ┌─────────────┐
    │ Controller  │  Recebe HTTP, valida dados
    └──────┬──────┘
           │
           ▼
    ┌─────────────┐
    │  Service    │  Cria Job com ID, escolhe executor
    └──────┬──────┘
           │
           ▼
    ┌─────────────┐
    │  Executor   │  Processa conforme o modo
    └──────┬──────┘
           │
           ▼
    ┌──────────────┐
    │ CPUSimulator │  Cálculo de primos (CPU-bound, 1-10ms)
    └──────┬───────┘
           │
           ▼
    ┌─────────────┐
    │ IOSimulator │  Simula I/O (I/O-bound, 200-400ms)
    └──────┬──────┘   ↺ retry se falhar
           │
           ▼
    ┌─────────────┐
    │  Resultado  │  Status COMPLETED, métricas atualizadas
    └─────────────┘
            │
            ▼
    { id: "abc-123", status: "COMPLETED" }
```

### O que cada etapa faz

| Etapa | Tipo de trabalho | O que simula |
|-------|------------------|--------------|
| **CPUSimulator** | CPU-bound | Cálculos intensivos (criptografia, parsing) |
| **IOSimulator** | I/O-bound | Espera por rede, banco de dados, arquivos |

O número aleatório de primos é gerado **antes** da execução, garantindo que retries usem o mesmo valor.

## Arquivos

```
JobController.java       →  Recebe HTTP
JobService.java          →  Gerencia jobs
SequentialJobExecutor.java
ThreadPoolJobExecutor.java   →  Executores (um por modo)
AsyncJobExecutor.java
CPUSimulator.java        →  Simula CPU-bound (primos)
IOSimulator.java         →  Simula I/O-bound (latência)
JobResult.java           →  Dados finais
```
