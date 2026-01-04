# 🛠️ Objetivo: Fortalecer Fundamentos Técnicos

**Foco**: JVM internals, concorrência (threads vs async), performance e estruturas de dados.

## 📌 Projeto 1 — Engine de Jobs Assíncronos

**Stack:**  
- Java 17+  
- Gradle  
- Spring Boot

**Funcionalidade principal:**  
Submeter “jobs” para processamento em lote, suportando três modos:

| Modo        | Implementação                         |
|-------------|--------------------------------------|
| Sequencial  | Executor simples                     |
| Com threads | ThreadPoolExecutor                   |
| Assíncrono  | CompletableFuture / Virtual Threads  |

---

## Requisitos obrigatórios

- JavaDoc detalhado explicando os três modos de implementação e seus trade-offs
- API REST com endpoints para:
    - **submit job**
    - **status**
    - **results**
- Simulação de carga pesada utilizando operações de I/O fake (esperas e latências artificiais)
- Métricas de performance para cada modo (tempo, threads ativas)
- Configuração via `application.yml` (tamanho de pool, timeouts, etc.)

---

## O que será treinado

- ✔ JVM (GC, heap vs stack)
- ✔ Concorrência (`thread pools`, assíncrono)
- ✔ Performance profiling com Java Flight Recorder / async traces

---

## Extras para maturidade

- Explicação e análise de relatório de desempenho

