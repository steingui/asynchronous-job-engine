package com.jobengine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main entry point for the Asynchronous Job Engine application.
 * 
 * <p>This application provides a job processing engine with three execution modes:</p>
 * <ul>
 *   <li><b>Sequential</b> - Simple executor for single-threaded processing</li>
 *   <li><b>Thread-based</b> - ThreadPoolExecutor for concurrent processing</li>
 *   <li><b>Async</b> - CompletableFuture / Virtual Threads for non-blocking I/O</li>
 * </ul>
 * 
 * @author gsk
 * @version 0.0.1-SNAPSHOT
 */
@SpringBootApplication
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}

