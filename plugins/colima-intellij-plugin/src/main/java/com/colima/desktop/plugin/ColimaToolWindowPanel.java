package com.colima.desktop.plugin;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.jcef.JBCefBrowser;
import com.intellij.ui.jcef.JBCefBrowserBase;
import com.intellij.ui.jcef.JBCefJSQuery;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.handler.CefLoadHandlerAdapter;

import javax.swing.*;
import java.awt.*;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * The main panel that hosts the JCEF browser rendering colima-desktop.html.
 *
 * Bridge between Java/IntelliJ and the HTML UI:
 *  - JS → Java:  window.intellijBridge.postMessage(json)  calls handleJSMessage()
 *  - Java → JS:  browser.executeJavaScript(...)           injects commands/data
 */
public class ColimaToolWindowPanel implements Disposable {

    private static final Logger LOG = Logger.getInstance(ColimaToolWindowPanel.class);

    private final Project project;
    private final JBCefBrowser browser;
    private JBCefJSQuery jsQuery;
    private final JPanel rootPanel;

    public ColimaToolWindowPanel(Project project) {
        this.project = project;

        // ── Root panel ────────────────────────────────────────────────────────
        rootPanel = new JPanel(new BorderLayout());

        // ── JCEF Browser ──────────────────────────────────────────────────────
        browser = new JBCefBrowser();
        Disposer.register(this, browser);

        // ── JS → Java query bridge ────────────────────────────────────────────
        jsQuery = JBCefJSQuery.create((JBCefBrowserBase) browser);
        jsQuery.addHandler(request -> {
            handleJSMessage(request);
            return null;
        });

        // ── Inject bridge + load HTML after page load ─────────────────────────
        browser.getJBCefClient().addLoadHandler(new CefLoadHandlerAdapter() {
            @Override
            public void onLoadEnd(CefBrowser cefBrowser, CefFrame frame, int httpStatusCode) {
                if (frame.isMain()) {
                    injectIntelliBridge(cefBrowser);
                }
            }
        }, browser.getCefBrowser());

        // ── Load the bundled HTML ─────────────────────────────────────────────
        loadColimaDesktopHTML();

        rootPanel.add(browser.getComponent(), BorderLayout.CENTER);
    }

    // ─── Load HTML ───────────────────────────────────────────────────────────

    private void loadColimaDesktopHTML() {
        try {
            // Read the bundled HTML from resources
            InputStream is = getClass().getResourceAsStream("/html/colima-desktop.html");
            if (is == null) {
                LOG.error("colima-desktop.html not found in resources!");
                loadFallbackPage();
                return;
            }
            String html = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            is.close();

            // Inject the IntelliJ bridge script before </head>
            String bridgeScript = buildBridgeScript();
            html = html.replace("</head>", bridgeScript + "\n</head>");

            // Load as data URI so JCEF renders it immediately (no server needed)
            String dataUri = "data:text/html;charset=utf-8," +
                java.net.URLEncoder.encode(html, StandardCharsets.UTF_8)
                    .replace("+", "%20");

            browser.loadURL(dataUri);

        } catch (Exception e) {
            LOG.error("Failed to load colima-desktop.html", e);
            loadFallbackPage();
        }
    }

    private void loadFallbackPage() {
        String fallback = """
            <!DOCTYPE html>
            <html>
            <head>
              <style>
                body {
                  background: #0d0f12; color: #e0e0e0;
                  font-family: 'JetBrains Mono', monospace;
                  display: flex; align-items: center; justify-content: center;
                  height: 100vh; margin: 0; flex-direction: column; gap: 16px;
                }
                .icon { font-size: 48px; }
                .title { font-size: 20px; color: #00d4ff; }
                .sub { font-size: 13px; color: #666; }
              </style>
            </head>
            <body>
              <div class="icon">🐳</div>
              <div class="title">Colima Desktop</div>
              <div class="sub">colima-desktop.html resource not found.<br/>
              Please rebuild the plugin with the HTML file in src/main/resources/html/</div>
            </body>
            </html>
            """;
        browser.loadHTML(fallback);
    }

    // ─── JS ↔ Java Bridge ────────────────────────────────────────────────────

    /**
     * Builds the <script> block injected into the HTML that creates
     * window.intellijBridge — the JS side of the bidirectional bridge.
     */
    private String buildBridgeScript() {
        String queryCallback = jsQuery.inject(
            "message",
            "function(r){ console.log('[intellij-bridge] response:', r); }",
            "function(ec, em){ console.error('[intellij-bridge] error', ec, em); }"
        );
        return """
            <script>
            // ── IntelliJ ↔ JS Bridge ────────────────────────────────────────
            window.intellijBridge = {
              /**
               * Send a message from JS to the IntelliJ/Java side.
               * @param {object} payload - e.g. { type: 'runCommand', cmd: 'docker ps' }
               */
              postMessage: function(payload) {
                var message = typeof payload === 'string' ? payload : JSON.stringify(payload);
                %s
              },

              /**
               * Called by Java to deliver results back into the page.
               * JS code can override this handler to process responses.
               */
              onMessage: function(json) {
                try {
                  var data = JSON.parse(json);
                  console.log('[intellij-bridge] received:', data);
                  // Dispatch to registered listeners
                  if (window.intellijBridge._listeners[data.type]) {
                    window.intellijBridge._listeners[data.type](data);
                  }
                } catch(e) {
                  console.error('[intellij-bridge] parse error:', e);
                }
              },

              _listeners: {},

              /**
               * Register a listener for a message type from Java.
               * e.g. intellijBridge.on('commandResult', fn)
               */
              on: function(type, fn) {
                this._listeners[type] = fn;
              },

              /** Convenience: run a CLI command via IntelliJ process runner */
              runCommand: function(cmd) {
                this.postMessage({ type: 'runCommand', cmd: cmd });
              },

              /** Notify IntelliJ of theme change */
              setTheme: function(theme) {
                this.postMessage({ type: 'setTheme', theme: theme });
              }
            };

            // Let the page know it's running inside IntelliJ
            window.isIntelliJPlugin = true;
            console.log('[Colima Desktop] Running inside IntelliJ IDEA via JCEF ✓');
            </script>
            """.formatted(queryCallback);
    }

    /**
     * After page load, inject additional setup that requires the DOM to exist.
     */
    private void injectIntelliBridge(CefBrowser cefBrowser) {
        String js = """
            (function() {
              // Tell the terminal/log sections we have a Java bridge
              if (window.intellijBridge) {
                // Wire up terminal command execution to Java
                intellijBridge.on('commandResult', function(data) {
                  // Forward result to terminal output if available
                  if (window.appendTerminalOutput) {
                    window.appendTerminalOutput(data.output, data.isError);
                  }
                });

                // Signal ready
                intellijBridge.postMessage({ type: 'ready', platform: 'darwin' });
              }

              // Add IntelliJ badge to the titlebar if present
              var titlebar = document.querySelector('.titlebar-title, .app-title, h1');
              if (titlebar) {
                var badge = document.createElement('span');
                badge.style.cssText = 'margin-left:8px;padding:2px 6px;background:#3d6b8e;' +
                  'color:#fff;border-radius:3px;font-size:10px;vertical-align:middle;';
                badge.textContent = 'IntelliJ';
                titlebar.appendChild(badge);
              }
            })();
            """;
        cefBrowser.executeJavaScript(js, "", 0);
    }

    /**
     * Handle messages sent from JS → Java via intellijBridge.postMessage().
     */
    private void handleJSMessage(String jsonMessage) {
        LOG.info("[ColimaDesktop] JS message: " + jsonMessage);
        try {
            // Basic routing without a full JSON library dependency
            if (jsonMessage.contains("\"type\":\"runCommand\"")) {
                String cmd = extractJsonString(jsonMessage, "cmd");
                if (cmd != null && !cmd.isBlank()) {
                    runShellCommandAsync(cmd);
                }
            } else if (jsonMessage.contains("\"type\":\"ready\"")) {
                LOG.info("[ColimaDesktop] UI ready ✓");
                // Could trigger initial status check here
                runShellCommandAsync("colima status");
            } else if (jsonMessage.contains("\"type\":\"setTheme\"")) {
                String theme = extractJsonString(jsonMessage, "theme");
                LOG.info("[ColimaDesktop] Theme: " + theme);
            }
        } catch (Exception e) {
            LOG.warn("[ColimaDesktop] Error handling JS message", e);
        }
    }

    /**
     * Run a shell command asynchronously and send output back to the JS terminal.
     */
    private void runShellCommandAsync(String cmd) {
        Thread.ofVirtual().start(() -> {
            try {
                ProcessBuilder pb = new ProcessBuilder("/bin/bash", "-l", "-c", cmd);
                pb.environment().put("PATH", "/usr/local/bin:/opt/homebrew/bin:/usr/bin:/bin");
                pb.redirectErrorStream(true);
                Process process = pb.start();

                String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                int exitCode = process.waitFor();

                // Send result back to JS
                sendToJS("commandResult", output, exitCode != 0);

            } catch (Exception e) {
                sendToJS("commandResult", "Error running command: " + e.getMessage(), true);
            }
        });
    }

    /**
     * Send a message from Java → JS via intellijBridge.onMessage().
     */
    private void sendToJS(String type, String output, boolean isError) {
        String escaped = output
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");

        String js = String.format(
            "if(window.intellijBridge) { window.intellijBridge.onMessage('{\"type\":\"%s\",\"output\":\"%s\",\"isError\":%b}'); }",
            type, escaped, isError
        );
        browser.getCefBrowser().executeJavaScript(js, "", 0);
    }

    // ─── Utilities ───────────────────────────────────────────────────────────

    /** Very lightweight JSON string extractor — avoids needing org.json dependency. */
    private String extractJsonString(String json, String key) {
        String search = "\"" + key + "\":\"";
        int start = json.indexOf(search);
        if (start < 0) return null;
        start += search.length();
        int end = json.indexOf("\"", start);
        if (end < 0) return null;
        return json.substring(start, end);
    }

    public JComponent getComponent() {
        return rootPanel;
    }

    @Override
    public void dispose() {
        if (jsQuery != null) {
            Disposer.dispose(jsQuery);
            jsQuery = null;
        }
    }
}
