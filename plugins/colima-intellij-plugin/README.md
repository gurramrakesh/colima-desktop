# Colima Desktop — IntelliJ IDEA Plugin

Embed the full **Colima Desktop** UI inside IntelliJ IDEA as a native Tool Window.
Manage containers, images, volumes, VM settings and run CLI commands — without leaving your IDE.

---

## Features

| Feature | Description |
|---|---|
| 🐳 **Tool Window** | Full Colima Desktop UI rendered via JCEF (Chromium) |
| 📊 **Status Bar** | Live `🐳 Colima: Running` / `⏸ Stopped` widget in the bottom bar |
| ⌨️ **Keyboard shortcut** | `Ctrl+Shift+D` to open/focus Colima Desktop |
| 🔧 **Tools menu** | Tools → Colima Desktop |
| 📂 **Run in Container** | Right-click any project file → Run in Colima Container |
| 🔗 **JS ↔ Java bridge** | Terminal commands in the UI actually execute via IntelliJ |

---

## Requirements

- **macOS** (Apple Silicon M1/M2/M3 or Intel)
- **IntelliJ IDEA 2023.1+** (Community or Ultimate) — JCEF must be enabled
- **Java 17+** (bundled with IntelliJ)
- **Colima** installed: `brew install colima`
- **Docker CLI**: `brew install docker`
- **Maven 3.8+** to build

---

## Project Structure

```
colima-intellij-plugin/
├── pom.xml                                          # Maven build
└── src/main/
    ├── java/com/colima/desktop/plugin/
    │   ├── ColimaToolWindowFactory.java             # Registers the tool window
    │   ├── ColimaToolWindowPanel.java               # JCEF browser + JS↔Java bridge
    │   ├── ColimaAppService.java                    # App-level Colima status service
    │   ├── ColimaStatusBarWidgetFactory.java        # Status bar widget
    │   └── OpenColimaDesktopAction.java             # Menu + right-click actions
    └── resources/
        ├── META-INF/plugin.xml                      # Plugin descriptor
        ├── html/colima-desktop.html                 # The Colima Desktop UI
        └── icons/
            ├── colima_tool_window.svg
            └── colima_action.svg
```

---

## Building

### 1. Prerequisites

```bash
# Install Java 17+ if needed
brew install openjdk@17

# Install Maven
brew install maven

# Verify
java -version   # should show 17+
mvn -version
```

### 2. Clone / place the plugin files

```bash
cd ~/IdeaProjects   # or wherever you keep projects
# The plugin folder is already structured — just cd into it
cd colima-intellij-plugin
```

### 3. Download the IntelliJ Platform SDK

Maven downloads the SDK automatically on first build. This may take a few minutes.

```bash
mvn dependency:resolve
```

### 4. Build the plugin

```bash
mvn package -DskipTests
```

Output: `target/colima-desktop-plugin-1.0.0.zip`

---

## Installing in IntelliJ IDEA

### Option A — Install from disk (recommended)

1. Open IntelliJ IDEA
2. Go to **Settings → Plugins**
3. Click the ⚙️ gear icon → **Install Plugin from Disk…**
4. Select `target/colima-desktop-plugin-1.0.0.zip`
5. Click **OK** → **Restart IDE**

### Option B — Development mode (live reload)

Open the project in IntelliJ IDEA and use the **Run Plugin** run configuration that IntelliJ auto-detects from `pom.xml`.

---

## Using the Plugin

### Opening Colima Desktop

- Click the **Colima Desktop** icon in the right tool window strip
- Or press `Ctrl+Shift+D`
- Or go to **Tools → Colima Desktop**

### Status Bar

The bottom status bar shows real-time Colima status, refreshed every 30 seconds.
Click it to open the Colima Desktop panel.

### Terminal Commands

The built-in terminal in the Colima Desktop UI executes commands via IntelliJ's
process runner on your Mac. Commands like `docker ps`, `colima start`, `colima stop`
run natively against your Colima socket at `~/.colima/default/docker.sock`.

### Run in Container

Right-click any file or folder in the Project tree → **Run in Colima Container**.
Specify a Docker image — IntelliJ opens the terminal and runs:
```
docker run -it --rm -v "/your/project:/workspace" -w /workspace <image>
```

---

## Enabling JCEF (if not already enabled)

JCEF is required to render the embedded browser. It is enabled by default in
IntelliJ IDEA 2021.2+. If you see a blank panel:

1. Open **Help → Find Action** → search `Registry`
2. Enable `ide.browser.jcef.enabled`
3. Restart IntelliJ IDEA

---

## Troubleshooting

| Symptom | Fix |
|---|---|
| Blank tool window | Enable JCEF in Registry (see above) |
| "colima not found" in status bar | Run `brew install colima` and ensure `/opt/homebrew/bin` is in PATH |
| Commands not executing | Check that Colima is running: `colima start` |
| Build fails on SDK download | Check internet connection; the JetBrains repository may be temporarily unavailable |

---

## Development Notes

### JS ↔ Java Bridge

The plugin injects a `window.intellijBridge` object into `colima-desktop.html`:

```javascript
// JS → Java (run a command)
intellijBridge.runCommand('docker ps -a');

// Java → JS (receive result)
intellijBridge.on('commandResult', function(data) {
  console.log(data.output);   // command stdout
  console.log(data.isError);  // true if non-zero exit
});
```

### Connecting to Real Colima

The HTML file's mock data can be replaced with real Docker API calls by:
1. Running a local proxy: `socat TCP-LISTEN:2375,fork UNIX-CONNECT:$HOME/.colima/default/docker.sock`
2. Or using `DOCKER_HOST=unix://$HOME/.colima/default/docker.sock docker ...` via the Java bridge

---

## License

MIT — See LICENSE
