package com.colima.desktop.plugin;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.StatusBarWidgetFactory;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.impl.status.EditorBasedWidget;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.event.MouseEvent;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Registers the Colima status bar widget factory.
 */
public class ColimaStatusBarWidgetFactory implements StatusBarWidgetFactory {

    @Override
    public @NotNull @NonNls String getId() {
        return "ColimaStatusWidget";
    }

    @Override
    public @NotNull String getDisplayName() {
        return "Colima Status";
    }

    @Override
    public boolean isAvailable(@NotNull Project project) {
        return true;
    }

    @Override
    public @NotNull StatusBarWidget createWidget(@NotNull Project project) {
        return new ColimaStatusBarWidget(project);
    }

    @Override
    public void disposeWidget(@NotNull StatusBarWidget widget) {
        widget.dispose();
    }

    @Override
    public boolean canBeEnabledOn(@NotNull StatusBar statusBar) {
        return true;
    }
}


/**
 * The actual status bar widget that shows 🐳 Running / ⏸ Stopped / etc.
 */
class ColimaStatusBarWidget extends EditorBasedWidget implements StatusBarWidget.TextPresentation {

    private static final long REFRESH_INTERVAL_MS = 30_000; // 30 seconds

    private String displayText = "🐳 Colima: …";
    private Timer timer;

    ColimaStatusBarWidget(@NotNull Project project) {
        super(project);
    }

    @Override
    public @NotNull @NonNls String ID() {
        return "ColimaStatusWidget";
    }

    @Override
    public void install(@NotNull StatusBar statusBar) {
        super.install(statusBar);
        startPolling();
    }

    private void startPolling() {
        timer = new Timer("ColimaStatusPoller", true);
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                ColimaAppService.getInstance().refreshStatus(() -> {
                    updateDisplay();
                });
            }
        }, 0, REFRESH_INTERVAL_MS);
    }

    private void updateDisplay() {
        ColimaAppService.ColimaStatus status = ColimaAppService.getInstance().getStatus();
        displayText = switch (status) {
            case RUNNING  -> "🐳 Colima: Running";
            case STOPPED  -> "⏸ Colima: Stopped";
            case STARTING -> "⏳ Colima: Starting…";
            case STOPPING -> "⏳ Colima: Stopping…";
            case ERROR    -> "⚠ Colima: Error";
            default       -> "🐳 Colima";
        };

        if (myStatusBar != null) {
            myStatusBar.updateWidget(ID());
        }
    }

    // ── TextPresentation ─────────────────────────────────────────────────────

    @Override
    public @NotNull String getText() {
        return displayText;
    }

    @Override
    public float getAlignment() {
        return 0;
    }

    @Override
    public @Nullable String getTooltipText() {
        return "Colima container runtime — " + ColimaAppService.getInstance().getStatusMessage() +
               "\nClick to open Colima Desktop";
    }

    @Override
    public @Nullable Consumer<MouseEvent> getClickConsumer() {
        return event -> {
            // Open the Colima Desktop tool window on click
            Project project = getProject();
            if (project != null) {
                var toolWindowManager = com.intellij.openapi.wm.ToolWindowManager.getInstance(project);
                var tw = toolWindowManager.getToolWindow("Colima Desktop");
                if (tw != null) {
                    tw.show();
                }
            }
        };
    }

    @Override
    public @Nullable WidgetPresentation getPresentation() {
        return this;
    }

    @Override
    public void dispose() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
        super.dispose();
    }
}
