package com.filesync.client.sync;

import com.filesync.client.http.SyncHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class SyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(SyncScheduler.class);

    private final SyncHttpClient httpClient;
    private final String ownerId;
    private final UUID folderId;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> syncFuture;
    private volatile boolean enabled = false;

    public SyncScheduler(SyncHttpClient httpClient, String ownerId, UUID folderId) {
        this.httpClient = httpClient;
        this.ownerId = ownerId;
        this.folderId = folderId;
    }

    public void start() {
        if (enabled) return;
        enabled = true;
        syncFuture = scheduler.scheduleAtFixedRate(() -> {
            if (!enabled) return;
            try {
                String basePath = System.getProperty("user.home") + "/FileSync";
                String folderName = (folderId != null) ? "shared_" + folderId.toString() : "personal_" + ownerId;
                Path syncPath = Paths.get(basePath, ownerId, folderName);
                if (!Files.exists(syncPath)) {
                    Files.createDirectories(syncPath);
                }
                SyncEngine engine = new SyncEngine(httpClient, ownerId, syncPath.toString(), folderId);
                engine.sync();
            } catch (Exception e) {
                log.error("Auto-sync failed", e);
            }
        }, 30, 300, TimeUnit.SECONDS);
    }

    public void stop() {
        enabled = false;
        if (syncFuture != null) {
            syncFuture.cancel(false);
            syncFuture = null;
        }
    }

    public boolean isRunning() {
        return enabled;
    }

    public void shutdown() {
        stop();
        scheduler.shutdownNow();
    }
}