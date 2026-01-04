# Trade-offs dos Modos de Execução

Este documento detalha as características, vantagens, desvantagens e cenários ideais de cada modo de execução do Job Engine.

---

## Visão Geral

| Aspecto | SEQUENTIAL | THREAD_POOL | ASYNC (Virtual Threads) |
|---------|------------|-------------|-------------------------|
| **Paralelismo** | Nenhum | Limitado (pool size) | Massivo (milhões) |
| **Stack** | Thread HTTP (~1MB) | N threads × ~1MB | Heap (~poucos KB) |
| **Overhead** | Zero | Médio | Baixo |
| **Complexidade** | Simples | Moderada | Simples |
| **Melhor para** | Debug, ordenação | CPU-bound | I/O-bound |

---

## 1. SEQUENTIAL

### Como Funciona

```
HTTP Request Thread
        │
        ▼
   ┌─────────┐
   │  Job 1  │ ← Bloqueia thread HTTP
   └────┬────┘
        │
   ┌─────────┐
   │  Job 2  │ ← Só inicia após Job 1 terminar
   └────┬────┘
        │
   ┌─────────┐
   │  Job N  │
   └────┬────┘
        │
        ▼
   HTTP Response
```

### Memória

| Componente | Uso |
|------------|-----|
| **Stack** | Usa stack da thread HTTP do Tomcat (~1MB reservado) |
| **Heap** | Mínimo - apenas objetos `Job` e `JobResult` |
| **Metaspace** | Nenhum adicional |

### Vantagens

1. **Simplicidade** - Sem race conditions, sem deadlocks
2. **Debugging fácil** - Stack trace linear e previsível
3. **Ordenação garantida** - Jobs executam na ordem de submissão
4. **Zero overhead** - Sem context switching, sem sincronização
5. **GC amigável** - Objetos de curta vida, coletados rapidamente no Young Gen

### Desvantagens

1. **Bloqueia thread HTTP** - Tomcat tem pool limitado (~200 threads default)
2. **Sem paralelismo** - Tempo total = soma de todos os jobs
3. **Não escala** - 1000 jobs = 1000× latência de um job
4. **Timeout HTTP** - Jobs longos podem causar timeout no cliente

### Impacto no GC

```
┌─────────────────────────────────────────┐
│              HEAP                        │
│  ┌─────────────┐  ┌─────────────────┐   │
│  │  Young Gen  │  │    Old Gen      │   │
│  │             │  │                 │   │
│  │ Job→Result  │  │   (vazio)       │   │
│  │  (efêmero)  │  │                 │   │
│  └─────────────┘  └─────────────────┘   │
└─────────────────────────────────────────┘
         ▲
         │
    Objetos morrem rápido → Minor GC barato
```

### Quando Usar

✅ **Use quando:**
- Debugging de lógica de negócio
- Ordem de execução importa
- Poucos jobs por request (< 5)
- Jobs são muito rápidos (< 10ms)
- Ambiente de desenvolvimento/teste

❌ **Evite quando:**
- Alta concorrência de requests
- Jobs demorados (> 100ms)
- Latência baixa é crítica

### Código Interno

```java
// SequentialJobExecutor.java
public List<JobResult> execute(List<Job> jobs) {
    return jobs.stream()
        .map(this::processJob)  // Bloqueia em cada job
        .toList();
}
```

---

## 2. THREAD_POOL (ThreadPoolExecutor)

### Como Funciona

```
HTTP Request Thread
        │
        ▼
   ┌─────────────────────────────────────┐
   │         ThreadPoolExecutor           │
   │  ┌───────┐ ┌───────┐ ┌───────┐      │
   │  │Thread1│ │Thread2│ │ThreadN│      │
   │  │ Job A │ │ Job B │ │ Job C │ ...  │
   │  └───────┘ └───────┘ └───────┘      │
   │         Work Queue (bounded)         │
   └─────────────────────────────────────┘
        │
        ▼ (aguarda todos via CompletableFuture.allOf)
   HTTP Response
```

### Memória

| Componente | Uso |
|------------|-----|
| **Stack** | N threads × ~1MB (16 threads = ~16MB reservado) |
| **Heap** | Thread objects, Runnable tasks, BlockingQueue |
| **Metaspace** | Classes dos workers |

### Configuração

```yaml
# application.yml
job-engine:
  thread-pool:
    core-size: 4      # Threads sempre ativas
    max-size: 16      # Máximo sob carga
    queue-capacity: 100  # Buffer de tarefas
    keep-alive: 60s   # Tempo antes de matar threads extras
```

### Anatomia de uma Platform Thread

```
┌─────────────────────────────────────────────────┐
│                 Platform Thread                  │
├─────────────────────────────────────────────────┤
│  Native Stack (~1MB)                            │
│  ├── Stack frames (chamadas de método)          │
│  ├── Local variables                            │
│  └── Return addresses                           │
├─────────────────────────────────────────────────┤
│  Thread Local Storage                           │
│  ├── ThreadLocal variables                      │
│  └── Security context                           │
├─────────────────────────────────────────────────┤
│  OS Resources                                   │
│  ├── Thread ID                                  │
│  ├── Scheduling priority                        │
│  └── CPU affinity                               │
└─────────────────────────────────────────────────┘
```

### Vantagens

1. **Paralelismo controlado** - Limita uso de recursos
2. **Reutilização** - Threads não são criadas/destruídas por request
3. **Backpressure** - Queue bounded previne OOM
4. **Bom para CPU-bound** - Maximiza uso de cores
5. **Tuning** - Core/max size configurável

### Desvantagens

1. **Context switching** - OS scheduler troca threads (~1-10μs por switch)
2. **Memory overhead** - Cada thread reserva ~1MB de stack
3. **Limite de threads** - Milhares de threads = problema
4. **Blocking I/O desperdiça** - Thread bloqueada em I/O não faz nada útil
5. **GC roots** - Mais threads = mais raízes para GC scanear

### Context Switching Explicado

```
     CPU Core 0
         │
    ┌────┴────┐
    ▼         ▼
┌───────┐ ┌───────┐
│Thread1│ │Thread2│
│ RUNNING│ │ READY │
└───────┘ └───────┘
    │
    │ (I/O block ou timeslice expira)
    │
    ▼ Context Switch (~1-10μs)
    │
    │ 1. Salva registradores Thread1
    │ 2. Salva stack pointer
    │ 3. Atualiza estado: RUNNING → WAITING
    │ 4. Carrega registradores Thread2
    │ 5. Restaura stack pointer
    │ 6. Atualiza estado: READY → RUNNING
    │
    ▼
┌───────┐ ┌───────┐
│Thread1│ │Thread2│
│WAITING│ │RUNNING│
└───────┘ └───────┘
```

### Impacto no GC

```
┌─────────────────────────────────────────┐
│              GC Roots                    │
│  ┌─────────────────────────────────┐    │
│  │ Thread 1 stack → refs to heap   │    │
│  │ Thread 2 stack → refs to heap   │    │
│  │ Thread 3 stack → refs to heap   │    │
│  │        ...                      │    │
│  │ Thread N stack → refs to heap   │    │
│  └─────────────────────────────────┘    │
└─────────────────────────────────────────┘
         │
         ▼
   Mais threads = Mais roots = GC mais lento
```

### Quando Usar

✅ **Use quando:**
- Processamento CPU-intensive
- Número de tarefas concorrentes é previsível
- Precisa limitar uso de recursos
- Mix de I/O e CPU work
- Sistemas que não suportam Java 21

❌ **Evite quando:**
- Milhares de conexões simultâneas
- Majoritariamente I/O-bound
- Latência ultra-baixa requerida

### Código Interno

```java
// ThreadPoolJobExecutor.java
public List<JobResult> execute(List<Job> jobs) {
    var futures = jobs.stream()
        .map(job -> CompletableFuture.supplyAsync(
            () -> processJob(job), 
            threadPoolExecutor  // Pool fixo
        ))
        .toList();
    
    return futures.stream()
        .map(CompletableFuture::join)
        .toList();
}
```

---

## 3. ASYNC (Virtual Threads - Project Loom)

### Como Funciona

```
                    ┌─────────────────────────────────────┐
                    │      Virtual Thread Scheduler        │
                    │  (ForkJoinPool, ~CPU cores threads) │
                    └─────────────────────────────────────┘
                                    │
          ┌─────────────────────────┼─────────────────────────┐
          ▼                         ▼                         ▼
    ┌──────────┐              ┌──────────┐              ┌──────────┐
    │ Carrier  │              │ Carrier  │              │ Carrier  │
    │ Thread 1 │              │ Thread 2 │              │ Thread N │
    └──────────┘              └──────────┘              └──────────┘
          │                         │                         │
    ┌─────┴─────┐             ┌─────┴─────┐             ┌─────┴─────┐
    ▼     ▼     ▼             ▼     ▼     ▼             ▼     ▼     ▼
  ┌───┐ ┌───┐ ┌───┐         ┌───┐ ┌───┐ ┌───┐         ┌───┐ ┌───┐ ┌───┐
  │VT1│ │VT2│ │VT3│         │VT4│ │VT5│ │VT6│         │VT7│ │VT8│ │VT9│
  └───┘ └───┘ └───┘         └───┘ └───┘ └───┘         └───┘ └───┘ └───┘
   
   (Milhões de Virtual Threads podem existir simultaneamente)
```

### Memória

| Componente | Uso |
|------------|-----|
| **Stack** | Armazenado no HEAP (~poucos KB, cresce conforme necessário) |
| **Heap** | Continuation objects (stack frames serializados) |
| **Carrier Threads** | ~CPU cores × 1MB (fixo e pequeno) |

### Anatomia de uma Virtual Thread

```
┌─────────────────────────────────────────────────┐
│                 Virtual Thread                   │
├─────────────────────────────────────────────────┤
│  Continuation (HEAP object)                     │
│  ├── Serialized stack frames                    │
│  ├── Local variables (copied to heap)          │
│  └── Grows/shrinks dynamically                  │
├─────────────────────────────────────────────────┤
│  State                                          │
│  ├── RUNNABLE (montado em carrier)              │
│  ├── WAITING (desmontado, salvo no heap)        │
│  └── TERMINATED (GC coletável)                  │
├─────────────────────────────────────────────────┤
│  Carrier Thread Reference                       │
│  └── null quando não está executando            │
└─────────────────────────────────────────────────┘
```

### Mount/Unmount (A Mágica do Loom)

```
  ANTES do I/O (blocking call)
  ┌────────────────────────────────┐
  │       Carrier Thread           │
  │  ┌──────────────────────────┐  │
  │  │    Virtual Thread        │  │
  │  │    (MOUNTED)             │  │
  │  │    Executando código     │  │
  │  └──────────────────────────┘  │
  └────────────────────────────────┘
              │
              ▼ Thread.sleep() ou I/O
              
  DURANTE o I/O
  ┌────────────────────────────────┐
  │       Carrier Thread           │
  │                                │  ← Livre para outra VT!
  │       (IDLE)                   │
  │                                │
  └────────────────────────────────┘
  
  ┌────────────────────────────────┐
  │    Virtual Thread (HEAP)       │
  │    Continuation salva          │  ← Stack preservado
  │    Estado: WAITING             │
  └────────────────────────────────┘

  APÓS I/O completar
  ┌────────────────────────────────┐
  │       Carrier Thread           │
  │  ┌──────────────────────────┐  │
  │  │    Virtual Thread        │  │  ← Pode ser outro carrier!
  │  │    (RE-MOUNTED)          │  │
  │  │    Continua execução     │  │
  │  └──────────────────────────┘  │
  └────────────────────────────────┘
```

### Vantagens

1. **Escalabilidade massiva** - Milhões de threads concorrentes
2. **Código simples** - Mesmo estilo bloqueante, sem callbacks
3. **Eficiência I/O** - Carrier liberado durante blocking
4. **Memory efficient** - Stack no heap, ~KB por thread
5. **GC friendly** - Threads completadas são garbage collected
6. **Zero tuning** - Não precisa configurar pool size

### Desvantagens

1. **Pinning** - `synchronized` e native code bloqueiam carrier
2. **Não ideal para CPU-bound** - Sem benefício se não há I/O
3. **ThreadLocal cuidado** - Milhões de threads = milhões de copies
4. **Debugging diferente** - Stack traces podem ser confusos
5. **Java 21+ obrigatório** - Sem backport

### Pinning Problem

```java
// ❌ RUIM: synchronized causa pinning
synchronized(lock) {
    Thread.sleep(1000);  // Carrier fica preso!
}

// ✅ BOM: Use ReentrantLock
lock.lock();
try {
    Thread.sleep(1000);  // Carrier é liberado
} finally {
    lock.unlock();
}
```

### Impacto no GC

```
┌─────────────────────────────────────────┐
│              HEAP                        │
│  ┌─────────────────────────────────┐    │
│  │     Continuations (VT stacks)   │    │
│  │  ┌────┐ ┌────┐ ┌────┐ ┌────┐   │    │
│  │  │ C1 │ │ C2 │ │ C3 │ │... │   │    │
│  │  └────┘ └────┘ └────┘ └────┘   │    │
│  │         (Coletáveis pelo GC)    │    │
│  └─────────────────────────────────┘    │
│                                          │
│  GC Roots: apenas ~CPU cores carriers   │
└─────────────────────────────────────────┘
         │
         ▼
   VTs terminadas → elegíveis para GC
   (diferente de platform threads que são OS resources)
```

### Quando Usar

✅ **Use quando:**
- Alta concorrência (milhares de requests)
- I/O-bound (HTTP calls, DB queries, file I/O)
- Microservices / APIs
- Conexões WebSocket
- Simplicidade é prioridade

❌ **Evite quando:**
- CPU-bound intensivo (melhor THREAD_POOL)
- Código com muito `synchronized`
- Bibliotecas nativas (JNI) bloqueantes
- Java < 21

### Código Interno

```java
// AsyncJobExecutor.java  
public List<JobResult> execute(List<Job> jobs) {
    var futures = jobs.stream()
        .map(job -> CompletableFuture.supplyAsync(
            () -> processJob(job),
            virtualThreadExecutor  // Executors.newVirtualThreadPerTaskExecutor()
        ))
        .toList();
    
    return futures.stream()
        .map(CompletableFuture::join)
        .toList();
}
```

---

## Comparativo de Performance

### Cenário: 1000 Jobs I/O-bound (250ms cada)

| Modo | Tempo Total | Threads Usadas | Memory |
|------|-------------|----------------|--------|
| SEQUENTIAL | ~250s | 1 | ~5MB |
| THREAD_POOL (16) | ~16s | 16 | ~20MB |
| ASYNC | ~1-2s | 12 carriers | ~10MB |

### Cenário: 1000 Jobs CPU-bound (sem I/O)

| Modo | Tempo Total | CPU Usage | Observação |
|------|-------------|-----------|------------|
| SEQUENTIAL | T | 8% (1 core) | Subutiliza CPU |
| THREAD_POOL (12) | T/12 | 100% | Ideal para CPU |
| ASYNC | T/12 | 100% | Mesmo que THREAD_POOL |

### Quando Performance é Similar

Virtual Threads **não são mais rápidas** para CPU-bound work. A vantagem aparece apenas quando há:
- Blocking I/O
- Waits (sleep, locks)
- Network calls

---

## Métricas Chave para Monitorar

### SEQUENTIAL
- `job.execution.time` - Tempo por job
- Tomcat thread pool usage

### THREAD_POOL
- `executor.pool.size` - Threads ativas
- `executor.queue.size` - Tarefas esperando
- `executor.completed` - Tasks completadas
- Context switches (via OS tools)

### ASYNC
- `jvm.threads.live` - Total de threads (deve ser baixo)
- Heap usage (continuations consomem heap)
- Carrier thread pinning events (via JFR)

---

## Decisão Flowchart

```
                    ┌─────────────────┐
                    │ Tipo de Workload│
                    └────────┬────────┘
                             │
              ┌──────────────┴──────────────┐
              ▼                              ▼
        ┌──────────┐                   ┌──────────┐
        │ CPU-bound│                   │ I/O-bound│
        └────┬─────┘                   └────┬─────┘
             │                              │
             ▼                              ▼
    ┌─────────────────┐          ┌─────────────────┐
    │   THREAD_POOL   │          │ Concorrência?   │
    │ (cores = CPUs)  │          └────────┬────────┘
    └─────────────────┘                   │
                              ┌───────────┴───────────┐
                              ▼                       ▼
                        ┌──────────┐            ┌──────────┐
                        │  Baixa   │            │   Alta   │
                        │ (< 100)  │            │ (> 100)  │
                        └────┬─────┘            └────┬─────┘
                             │                       │
                             ▼                       ▼
                    ┌─────────────────┐    ┌─────────────────┐
                    │   THREAD_POOL   │    │      ASYNC      │
                    │   ou SEQUENTIAL │    │ (Virtual Thread)│
                    └─────────────────┘    └─────────────────┘
```

---

## Referências

- [JEP 444: Virtual Threads](https://openjdk.org/jeps/444)
- [Java Concurrency in Practice](https://jcip.net/)
- [Project Loom Documentation](https://wiki.openjdk.org/display/loom)
- [Inside the Java Virtual Machine](https://www.artima.com/insidejvm/ed2/)

