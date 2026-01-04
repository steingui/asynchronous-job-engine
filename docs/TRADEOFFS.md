# Trade-offs dos Modos de Execução

Análise técnica dos 3 modos de execução do Job Engine.

## Resumo

| Modo | Estratégia | Melhor para |
|------|------------|-------------|
| **SEQUENTIAL** | 1 thread, jobs em série | Debug, ordenação |
| **THREAD_POOL** | Pool de N threads paralelas | CPU-bound (cálculos intensivos) |
| **ASYNC** | Virtual Threads (milhões) | I/O-bound (espera por rede/banco) |

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

---

## SEQUENTIAL

### Funcionamento

```
HTTP Thread
    │
    ▼
┌─────────┐
│  Job 1  │  ← Bloqueia
└────┬────┘
     │
┌─────────┐
│  Job 2  │  ← Só inicia após Job 1
└────┬────┘
     │
┌─────────┐
│  Job N  │
└────┬────┘
     │
     ▼
Response
```

### Memória

| Componente | Uso |
|------------|-----|
| **Stack** | Thread HTTP do Tomcat (~1MB) |
| **Heap** | Mínimo - apenas Job e JobResult |

### Vantagens

- Sem race conditions, sem deadlocks
- Stack trace linear e previsível (quando dá erro, a pilha de chamadas mostra exatamente a sequência do código, sem saltos entre threads)
- Jobs executam na ordem
- Zero overhead de sincronização (sem custo extra para coordenar threads, pois só existe uma)
- Garbage Collector amigável 

### Desvantagens

- Bloqueia thread HTTP (Tomcat tem ~200)
- Tempo total = soma de todos os jobs
- Não escala

### Quando usar

✅ Debug, ordem importa, poucos jobs (<5), jobs rápidos (<10ms)

❌ Alta concorrência, jobs demorados, latência crítica

---

## THREAD_POOL

### Funcionamento

```
HTTP Thread
    │
    ▼
┌─────────────────────────────────────┐
│         ThreadPoolExecutor           │
│  ┌───────┐ ┌───────┐ ┌───────┐      │
│  │Thread1│ │Thread2│ │ThreadN│      │
│  │ Job A │ │ Job B │ │ Job C │      │
│  └───────┘ └───────┘ └───────┘      │
│         Work Queue                   │
└─────────────────────────────────────┘
    │
    ▼
Response (após todos terminarem)
```

### Memória

| Componente | Uso |
|------------|-----|
| **Stack** | N threads × ~1MB |
| **Heap** | Thread objects, Runnable, Queue |


### Vantagens

- Paralelismo controlado (você define quantas threads rodam ao mesmo tempo)
- Threads reutilizadas(não cria/destrói thread a cada job, reusa as existentes)
- Queue bounded previne OOM (fila com limite máximo impede acúmulo infinito de tarefas que estouraria a memória)
- Bom para CPU-bound (N threads paralelas usam N cores do CPU simultaneamente, maximizando processamento)
- Configurável (ajusta tamanho do pool conforme sua máquina)

### Desvantagens

- Context switching tem custo (trocar entre threads leva ~1-10μs cada vez)
- Cada thread reserva ~1MB(16 threads = 16MB só de stack)
- Thread bloqueada em I/O desperdiça recursos(fica parada esperando, ocupando memória)
- Mais threads = GC mais lento(Garbage Collector precisa verificar mais stacks)

### Quando usar

✅ CPU-intensive, concorrência previsível, limitar recursos, Java <21

❌ Milhares de conexões, majoritariamente I/O

---

## ASYNC (Virtual Threads)

### Funcionamento

```
┌─────────────────────────────────────┐
│      Virtual Thread Scheduler        │
│       (~CPU cores carriers)          │
└─────────────────────────────────────┘
          │         │         │
    ┌─────┴───┐ ┌───┴───┐ ┌───┴─────┐
    │Carrier 1│ │Carrier2│ │Carrier N│
    └─────────┘ └───────┘ └─────────┘
         │
   ┌─────┼─────┐
   ▼     ▼     ▼
 ┌───┐ ┌───┐ ┌───┐
 │VT1│ │VT2│ │VT3│  (milhões possíveis)
 └───┘ └───┘ └───┘
```

### Memória

| Componente | Uso |
|------------|-----|
| **Stack** | No HEAP (~KB, cresce conforme necessário) |
| **Carriers** | ~CPU cores × 1MB (fixo) |

### Virtual Thread (anatomia)

```
┌─────────────────────────────────┐
│  Continuation (HEAP)            │
│  ├── Stack frames serializados  │
│  └── Cresce/encolhe dinamicamente│
├─────────────────────────────────┤
│  Estado                         │
│  ├── RUNNABLE (montado)         │
│  ├── WAITING (desmontado)       │
│  └── TERMINATED (GC coletável)  │
└─────────────────────────────────┘
```

### Mount/Unmount (a mágica)

- **Carrier Thread**: Thread real do sistema operacional (~12, igual aos cores)
- **Virtual Thread**: Thread leve gerenciada pela JVM (milhões possíveis)
A Virtual Thread precisa de uma Carrier para executar código.

**Mount (Montar)**
Virtual Thread é associada a uma Carrier e executa

**Unmount (Desmontar)**
Quando a Virtual Thread faz I/O ou sleep()
1. JVM salva o estado (stack) da Virtual Thread no heap
2. Virtual Thread é desassociada da Carrier
3. Carrier fica livre para outra Virtual Thread

**Re-mount (Remontar)**
1. Quando I/O termina:
2. JVM pega qualquer Carrier disponível
3. Restaura o estado da Virtual Thread

Continua execução de onde parou

```
EXECUTANDO:
┌────────────────────┐
│   Carrier Thread   │
│ ┌────────────────┐ │
│ │ Virtual Thread │ │  ← MOUNTED
│ │   (rodando)    │ │
│ └────────────────┘ │
└────────────────────┘

DURANTE I/O:
┌────────────────────┐
│   Carrier Thread   │  ← LIVRE para outra VT!
│      (idle)        │
└────────────────────┘

┌────────────────────┐
│  Virtual Thread    │  ← Stack salvo no heap
│    (WAITING)       │
└────────────────────┘

APÓS I/O:
┌────────────────────┐
│   Carrier Thread   │  ← Pode ser outro carrier!
│ ┌────────────────┐ │
│ │ Virtual Thread │ │  ← RE-MOUNTED
│ │  (continua)    │ │
│ └────────────────┘ │
└────────────────────┘
```

### Pinning (cuidado!)

```java
// ❌ RUIM: synchronized bloqueia carrier
synchronized(lock) {
    Thread.sleep(1000);  // Carrier preso!
}

// ✅ BOM: ReentrantLock libera carrier
lock.lock();
try {
    Thread.sleep(1000);  // Carrier livre
} finally {
    lock.unlock();
}
```

### Vantagens

- Milhões de threads concorrentes (stack no heap = poucos KB cada, não 1MB)
- Código simples (escreve como bloqueante, JVM cuida da concorrência)
- Carrier liberado durante I/O (thread real fica livre enquanto Virtual Thread espera)
- ~KB por thread (stack cresce só o necessário, não reserva 1MB fixo)
- Zero tuning necessário (sem configurar pool size, JVM gerencia)

### Desvantagens

- Pinning com `synchronized` (bloco synchronized prende a carrier, use ReentrantLock)
- Sem benefício para CPU-bound (se não espera I/O, não há carrier para liberar)
- ThreadLocal com milhões de cópias  (cada Virtual Thread tem sua cópia, cuidado com memória)
- Java 21+ obrigatório(Virtual Threads não existem em versões anteriores)

### Quando usar

✅ Alta concorrência, I/O-bound, microservices, WebSocket

❌ CPU-bound, muito `synchronized`, bibliotecas nativas

---

## Comparativo de Performance

### I/O-bound (1000 jobs, 250ms cada)

| Modo | Tempo Total | Threads | Memória |
|------|-------------|---------|---------|
| SEQUENTIAL | ~250s | 1 | ~5MB |
| THREAD_POOL | ~16s | 16 | ~20MB |
| ASYNC | ~1-2s | 12 carriers | ~10MB |

### CPU-bound (sem I/O)

| Modo | Tempo | CPU |
|------|-------|-----|
| SEQUENTIAL | T | 8% |
| THREAD_POOL | T/12 | 100% |
| ASYNC | T/12 | 100% |

Virtual Threads **não são mais rápidas** para CPU-bound. A vantagem aparece com I/O.

---

## Chaos Testing

Simula falhas do mundo real para testar resiliência.

### Configuração

```yaml
io-simulation:
  failure-rate: 0.10        # 10% dos jobs falham
  timeout-rate: 0.05        # 5% demoram muito mais
  timeout-latency-ms: 5000  # 5 segundos de latência extrema
```

### O que simula

| Parâmetro | Simula |
|-----------|--------|
| `failure-rate` | Rede caiu, banco indisponível, serviço fora |
| `timeout-rate` | Query lenta, API externa travada |

### Resultado esperado

Com 100 jobs por modo (300 total) e 10% failure-rate:
- ~30 jobs em `failedCount`
- Alguns com `maxExecutionTimeMs` alto (timeout)

### Por que importa

Sistemas reais falham. Testar com chaos revela:
- Como o sistema se comporta sob falha
- Se métricas de erro funcionam
- Se timeouts estão configurados corretamente

---

## Resiliência (Resilience4j)

O sistema implementa padrões de resiliência para reduzir falhas.

### Retry com Backoff Exponencial

```
Falha detectada
      │
      ▼
┌──────────────┐
│  Tentativa 1 │ ← falhou
└──────┬───────┘
       │ espera 500ms
       ▼
┌──────────────┐
│  Tentativa 2 │ ← falhou
└──────┬───────┘
       │ espera 1s (500ms × 2)
       ▼
┌──────────────┐
│  Tentativa 3 │ ← falhou
└──────┬───────┘
       │
       ▼
   FALLBACK → Job marcado FAILED
```

### Configuração

```yaml
resilience4j:
  retry:
    instances:
      ioSimulator:
        max-attempts: 3
        wait-duration: 500ms
        exponential-backoff-multiplier: 2
        retry-exceptions:
          - com.jobengine.exception.IOSimulationException
```

### Impacto

| Métrica | Sem Retry | Com Retry |
|---------|-----------|-----------|
| `failedCount` | ~10% | ~1% |
| Tempo médio | +0% | +~15% |

**Por quê ~1%?** Probabilidade de falhar 3× seguidas: 10% × 10% × 10% = 0.1%

### Trade-off

- ✅ Menos falhas visíveis ao cliente
- ✅ Resiliente a falhas transitórias
- ⚠️ Tempo de execução maior (retries + waits)
- ⚠️ Pode sobrecarregar serviço já com problemas

### Quando desabilitar

```yaml
max-attempts: 1  # Desabilita retry
```

Útil para:
- Jobs idempotentes que não podem repetir
- Quando a falha é definitiva (não transitória)

---

## Referências

- [JEP 444: Virtual Threads](https://openjdk.org/jeps/444)
- [Java Concurrency in Practice](https://jcip.net/)
- [Project Loom](https://wiki.openjdk.org/display/loom)
