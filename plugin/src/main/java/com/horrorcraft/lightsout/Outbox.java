package com.horrorcraft.lightsout;

import com.google.gson.Gson;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Event log for the bridge: one JSON object per line in outbox/&lt;run_id&gt;.jsonl.
 * The plugin is the only writer. All disk work happens on one background thread, in order,
 * and an I/O error is logged, never thrown.
 */
public final class Outbox implements AutoCloseable {

    private static final Gson GSON = new Gson();

    private final Path dir;
    private final Logger log;
    private final ExecutorService writer = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "LightsOut-writer");
        t.setDaemon(true);
        return t;
    });
    private volatile String runId;

    public Outbox(Path dir, Logger log, String runId) {
        this.dir = dir;
        this.log = log;
        this.runId = runId;
    }

    public void setRun(String runId) {
        this.runId = runId;
    }

    /** Builds the line now (timestamp is the moment of the event) and appends it later. */
    public void emit(String kind, Map<String, ?> fields) {
        String line = line(Instant.now(), runId, kind, fields);
        Path file = dir.resolve(fileName(runId));
        submit(() -> {
            try {
                Files.createDirectories(dir);
                Files.writeString(file, line + "\n", StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException e) {
                log.log(Level.WARNING, "outbox write failed: " + file, e);
            }
        });
    }

    /** Runs a disk task on the writer thread, after everything submitted before it. */
    public void submit(Runnable task) {
        try {
            writer.execute(() -> {
                try {
                    task.run();
                } catch (RuntimeException e) {
                    log.log(Level.WARNING, "writer task failed", e);
                }
            });
        } catch (RejectedExecutionException e) {
            log.warning("writer closed; dropped a write");
        }
    }

    static String line(Instant at, String runId, String kind, Map<String, ?> fields) {
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("at", at.truncatedTo(ChronoUnit.MILLIS).toString());
        o.put("run_id", runId);
        o.put("kind", kind);
        if (fields != null) o.putAll(fields);
        return GSON.toJson(o);
    }

    static String fileName(String runId) {
        if (runId == null || runId.isBlank()) return "no-run.jsonl";
        return runId.replaceAll("[^A-Za-z0-9_-]", "_") + ".jsonl";
    }

    @Override
    public void close() {
        writer.shutdown();
        try {
            if (!writer.awaitTermination(5, TimeUnit.SECONDS)) log.warning("writer did not finish in 5 s");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
