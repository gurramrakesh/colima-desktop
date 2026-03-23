package com.colima.desktop.plugin;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;

/**
 * Application-level service that manages Colima runtime state
 * across all open IntelliJ projects.
 */
@Service(Service.Level.APP)
public final class ColimaAppService {

    private static final Logger LOG = Logger.getInstance(ColimaAppService.class);

    public enum ColimaStatus {
        UNKNOWN, RUNNING, STOPPED, STARTING, STOPPING, ERROR
    }

    private volatile ColimaStatus currentStatus = ColimaStatus.UNKNOWN;
    private volatile String statusMessage = "Unknown";

    public static ColimaAppService getInstance() {
        return ApplicationManager.getApplication().getService(ColimaAppService.class);
    }

    public ColimaStatus getStatus() {
        return currentStatus;
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    /**
     * Runs `colima status` in background and updates the cached status.
     * Called by the status bar widget every 30 seconds.
     */
    public void refreshStatus(Runnable onComplete) {
        Thread.ofVirtual().start(() -> {
            try {
                ProcessBuilder pb = new ProcessBuilder("/bin/bash", "-l", "-c", "colima status 2>&1");
                pb.environment().put("PATH", "/usr/local/bin:/opt/homebrew/bin:/usr/bin:/bin");
                Process p = pb.start();
                String out = new String(p.getInputStream().readAllBytes());
                int exit = p.waitFor();

                if (exit == 0 && out.contains("running")) {
                    currentStatus = ColimaStatus.RUNNING;
                    statusMessage = "Running";
                } else if (out.contains("stopped") || out.contains("not running")) {
                    currentStatus = ColimaStatus.STOPPED;
                    statusMessage = "Stopped";
                } else if (exit != 0 && out.contains("command not found")) {
                    currentStatus = ColimaStatus.ERROR;
                    statusMessage = "colima not found";
                } else {
                    currentStatus = ColimaStatus.UNKNOWN;
                    statusMessage = "Unknown";
                }

                LOG.debug("[ColimaAppService] Status: " + statusMessage);

            } catch (Exception e) {
                currentStatus = ColimaStatus.ERROR;
                statusMessage = "Error: " + e.getMessage();
                LOG.warn("[ColimaAppService] Status check failed", e);
            }

            if (onComplete != null) {
                ApplicationManager.getApplication().invokeLater(onComplete);
            }
        });
    }
}
