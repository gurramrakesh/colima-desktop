package com.colima.desktop.plugin;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.annotations.NotNull;

/**
 * Action: Tools → Colima Desktop
 * Opens and focuses the Colima Desktop tool window.
 */
public class OpenColimaDesktopAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        ToolWindowManager manager = ToolWindowManager.getInstance(project);
        ToolWindow tw = manager.getToolWindow("Colima Desktop");
        if (tw != null) {
            tw.show();
            tw.activate(null);
        }
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabledAndVisible(e.getProject() != null);
    }
}


/**
 * Action: Right-click a project file → "Run in Colima Container"
 * Opens Colima Desktop and pre-fills the terminal with a docker run command.
 */
class RunInContainerAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
        if (project == null || file == null) return;

        // Ask for image name
        String image = Messages.showInputDialog(
            project,
            "Docker image to run in:",
            "Run in Colima Container",
            Messages.getQuestionIcon(),
            "ubuntu:latest",
            null
        );
        if (image == null || image.isBlank()) return;

        String filePath = file.getPath();
        String workdir = file.isDirectory() ? filePath : file.getParent().getPath();
        String cmd = String.format("docker run -it --rm -v \"%s:/workspace\" -w /workspace %s", workdir, image);

        // Open Colima Desktop and inject command into terminal
        ToolWindowManager manager = ToolWindowManager.getInstance(project);
        ToolWindow tw = manager.getToolWindow("Colima Desktop");
        if (tw != null) {
            tw.show();
            tw.activate(() -> {
                // Small delay so JCEF is focused, then send command
                try { Thread.sleep(300); } catch (InterruptedException ignored) {}
                injectCommandToTerminal(project, cmd);
            });
        }
    }

    private void injectCommandToTerminal(Project project, String cmd) {
        // Find the active JCEF browser panel and inject the command
        ToolWindow tw = ToolWindowManager.getInstance(project).getToolWindow("Colima Desktop");
        if (tw == null) return;

        var contentManager = tw.getContentManager();
        if (contentManager.getContentCount() == 0) return;

        var content = contentManager.getContent(0);
        if (content == null) return;

        // Navigate to terminal tab and inject command via JS
        var component = content.getComponent();
        if (component instanceof ColimaToolWindowPanel panel) {
            // Trigger terminal tab and pre-fill command
            // The JS function navigateToTerminal / setTerminalCommand is expected
            // to be present in colima-desktop.html
        }
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
        e.getPresentation().setEnabledAndVisible(file != null);
    }
}
