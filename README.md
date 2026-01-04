# Job Engine

Engine de jobs com 3 modos de execução para estudo de concorrência na JVM (Java Virtual Machine).

## Executar

```bash
# Iniciar servidor
./gradlew bootRun

# Ou usar o script de stress test (inicia automaticamente)
./scripts/stress-test.sh 100
```

## Modos de Execução

| Modo | Como funciona | Melhor para |
|------|---------------|-------------|
| **SEQUENTIAL** | Uma thread (linha de execução), jobs em série | Debug, quando ordem importa |
| **THREAD_POOL** | Pool fixo de threads (~16 linhas paralelas) | Cálculos intensivos (CPU-bound) |
| **ASYNC** | Virtual Threads (threads leves, milhões possíveis) | Operações de espera (I/O-bound) |

### O que significa CPU-bound e I/O-bound?

- **CPU-bound** = Trabalho que usa processador intensamente (cálculos, compressão, criptografia)
- **I/O-bound** = Trabalho que espera por algo externo (rede, banco de dados, arquivos)

### Comparativo Técnico

```
                SEQUENTIAL          THREAD_POOL         ASYNC
                    │                   │                 │
Stack (memória)  1 thread            N × 1MB           ~KB (leve)
Paralelismo      Nenhum              Limitado          Milhões
Troca de contexto Zero               ~1-10μs           Mínimo
Limpeza memória  Rápida              Mais lenta        Rápida
Durante espera   Trava tudo          Trava N threads   Libera recursos
```

**Glossário da tabela:**
- **Stack**: Memória reservada para cada thread executar
- **Troca de contexto**: Tempo que o sistema leva para alternar entre threads
- **GC** (Garbage Collector): "Lixeiro" automático do Java que libera memória de objetos que não são mais usados. Mais threads = mais trabalho para o GC = possíveis pausas na aplicação

### Quando usar cada modo?

```
┌──────────────────────────┐
│  Seu trabalho é mais:    │
└────────────┬─────────────┘
             │
    ┌────────┴────────┐
    ▼                 ▼
┌────────┐       ┌────────┐
│Cálculo │       │Espera  │
│intenso │       │(rede,  │
│(CPU)   │       │ disco) │
└───┬────┘       └───┬────┘
    │                │
    ▼                ▼
THREAD_POOL    ┌─────────────┐
               │ Quantas     │
               │ tarefas?    │
               └──────┬──────┘
                      │
            ┌─────────┴─────────┐
            ▼                   ▼
       ┌────────┐          ┌────────┐
       │ Poucas │          │ Muitas │
       │ (<100) │          │ (>100) │
       └───┬────┘          └───┬────┘
           │                   │
           ▼                   ▼
      THREAD_POOL           ASYNC
```

## API

```bash
# Submeter job
curl -X POST http://localhost:8080/api/jobs \
  -H "Content-Type: application/json" \
  -d '{"name": "meu-job", "payload": "dados", "executionMode": "ASYNC"}'

# Ver métricas comparativas
curl http://localhost:8080/api/metrics/compare

# Resetar métricas (zerar contadores)
curl -X DELETE http://localhost:8080/api/metrics
```

### Endpoints (URLs disponíveis)

| Método | Endpoint | Descrição |
|--------|----------|-----------|
| POST | `/api/jobs` | Criar job |
| GET | `/api/jobs` | Listar jobs |
| GET | `/api/jobs/{id}` | Detalhes do job |
| GET | `/api/metrics/compare` | Comparar performance dos modos |
| DELETE | `/api/metrics` | Zerar métricas |

## Stress Test (Teste de Carga)

```bash
# 100 jobs por modo (300 total)
./scripts/stress-test.sh 100

# 500 jobs por modo
./scripts/stress-test.sh 500
```

O script automaticamente:
1. Inicia a API se não estiver rodando
2. Zera métricas anteriores
3. Submete jobs em paralelo para os 3 modos
4. Aguarda todos os jobs terminarem
5. Exibe resultados comparativos
6. Abre o navegador com os resultados

## Configuração

```yaml
# application.yml
job-engine:
  thread-pool:
    core-size: 4      # Threads mínimas sempre ativas
    max-size: 16      # Máximo de threads sob carga
  io-simulation:
    min-latency-ms: 50   # Simula espera mínima (milissegundos)
    max-latency-ms: 500  # Simula espera máxima
```

## Entendendo os Resultados

Após o stress test, você verá algo como:

```json
{
  "modeStats": {
    "ASYNC": {
      "completedCount": 100,
      "avgExecutionTimeMs": 275.5
    },
    "THREAD_POOL": {
      "completedCount": 100,
      "avgExecutionTimeMs": 280.2
    },
    "SEQUENTIAL": {
      "completedCount": 100,
      "avgExecutionTimeMs": 278.1
    }
  },
  "summary": {
    "fastestMode": "ASYNC"
  }
}
```

- **completedCount**: Quantos jobs terminaram
- **avgExecutionTimeMs**: Tempo médio por job (menor = melhor)
- **fastestMode**: Qual modo foi mais rápido

## Documentação Adicional

- [Jornada de um Job](docs/JORNADA-JOB.md) - O que acontece quando você envia um job
- [Trade-offs detalhados](docs/TRADEOFFS.md) - Análise técnica profunda de cada modo
- [JavaDoc](build/docs/javadoc/index.html) - Documentação do código (gerar com `./gradlew javadoc`)

## Tecnologias

- **Java 21** - Linguagem de programação
- **Spring Boot 3.4** - Framework web
- **Micrometer** - Biblioteca de métricas
- **Gradle** - Ferramenta de build (compilação)
