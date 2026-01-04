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
    ┌─────────────┐
    │ IOSimulator │  Simula trabalho (200-400ms)
    └──────┬──────┘
           │
           ▼
    ┌─────────────┐
    │  Resultado  │  Status COMPLETED, métricas atualizadas
    └─────────────┘
            │
            ▼
    { id: "abc-123", status: "COMPLETED" }
```

## Arquivos

```
JobController.java       →  Recebe HTTP
JobService.java          →  Gerencia jobs
SequentialJobExecutor.java
ThreadPoolJobExecutor.java   →  Executores (um por modo)
AsyncJobExecutor.java
IOSimulator.java         →  Simula I/O
JobResult.java           →  Dados finais
```
