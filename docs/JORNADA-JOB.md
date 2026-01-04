# Jornada de um Job

O que acontece quando você envia um job para o sistema.

---

## Fluxo

```
    Você
     │
     │  POST /api/jobs
     │  { name: "meu-job", executionMode: "ASYNC" }
     │
     ▼
┌─────────────┐
│ Controller  │  ← Recebe o pedido HTTP
│             │  ← Valida os dados
└──────┬──────┘
       │
       ▼
┌─────────────┐
│  Service    │  ← Cria o Job com ID único
│             │  ← Escolhe o executor pelo modo
└──────┬──────┘
       │
       ▼
┌─────────────┐
│  Executor   │  ← Processa o job
│             │  ← Modo define a estratégia
└──────┬──────┘
       │
       ▼
┌─────────────┐
│ IOSimulator │  ← Simula trabalho (espera 10-1000ms)
│             │  ← No mundo real: banco, API, arquivo
└──────┬──────┘
       │
       ▼
┌─────────────┐
│  Resultado  │  ← Job marcado como COMPLETED
│             │  ← Métricas atualizadas
└─────────────┘
       │
       ▼
    Você recebe
    { id: "abc-123", status: "COMPLETED" }
```

---

## Passo a passo

1. **Você envia** um pedido HTTP com nome e modo
2. **Controller** recebe e valida os campos
3. **Service** cria o job e escolhe o executor baseado no modo
4. **Executor** processa usando a estratégia do modo escolhido
5. **IOSimulator** simula uma operação demorada
6. **Resultado** é retornado com status e métricas

---

## Como cada modo executa

| Modo | Estratégia |
|------|------------|
| **SEQUENTIAL** | Executa na thread atual, um job por vez |
| **THREAD_POOL** | Distribui entre threads do pool (máx 16) |
| **ASYNC** | Cria Virtual Thread por job (milhares possíveis) |

---

## Arquivos envolvidos

```
Controller    →  JobController.java      (recebe HTTP)
Service       →  JobService.java         (gerencia jobs)
Executor      →  AsyncJobExecutor.java   (ou SequentialJobExecutor, ThreadPoolJobExecutor)
Simulador     →  IOSimulator.java        (simula I/O)
Resultado     →  JobResult.java          (dados finais)
```
