# LLMHub Desktop (Linux / Arch Linux)

**LLMHub Desktop** is a native Linux client for large language models, designed with Material 3 Expressive aesthetics. It connects to OpenAI, Anthropic, Google Gemini, and any OpenAI-compatible or local endpoint (Ollama, LM Studio) with automatic rotation, failover, customizable reasoning effort, and built-in tools.

---

## Features

- **Exact LLMHub Design**: Material 3 Expressive design tokens, colors, rounded card scale, and typography.
- **Adaptive Desktop Interface**: Left collapsible sidebar for navigation and conversation history, fast model switching, full chat canvas.
- **Provider Rotation & Failover**: Automatic rotation across provider pools (Failover, Round-robin, Weighted, Least used) with key failover (401, 402, 403, 429) and mid-stream response handoff.
- **OpenAI Wire & Responses API Modes**: Choose between `/chat/completions` (Wire) and `/responses` (Responses).
- **Customizable Reasoning Effort**: Configure reasoning effort (`default`, `none`, `low`, `medium`, `high`), custom parameter names (`reasoning_effort`, `effort`, `thinking_budget`), and custom values.
- **Fuzzy Search Model Picker**: Real-time fuzzy search across all providers with score weighting.
- **Key Scanner & Bulk Add**: Auto-detects and categorizes API keys for basic providers from files or pasted text.
- **Local Linux XDG Storage**:
  - Settings & Providers: `~/.config/llmhub/`
  - Conversations & Workspace: `~/.local/share/llmhub/`

---

## Quick Install (One-Liner)

Install into `~/.local` (no root/sudo required):
```bash
curl -fsSL https://raw.githubusercontent.com/radiocycle/llmhub-desktop/main/install.sh | bash
```
This automatically:
1. Downloads the latest release tarball for `x86_64` Linux.
2. Extracts app libraries into `~/.local/lib/llmhub/`.
3. Creates the launcher at `~/.local/bin/llmhub`.
4. Installs the desktop launcher entry and icon into `~/.local/share/applications/` and `~/.local/share/icons/`.

---

## Installation on Arch Linux

### Option 1: Build from source with `makepkg`
```bash
git clone https://github.com/radiocycle/llmhub-desktop.git
cd llmhub-desktop/packaging
makepkg -si
```

### Option 2: Pre-compiled binary with `makepkg`
```bash
cd packaging
makepkg -p PKGBUILD-bin -si
```

### Option 3: AUR Helpers (`yay` / `paru`)
Once published to AUR or pointing to the repository:
```bash
yay -S llmhub
# or
paru -S llmhub
```

---

## Other Linux Distributions

### Standalone Tarball (`.tar.gz`)
1. Download `llmhub-linux-x86_64.tar.gz` from [Releases](https://github.com/radiocycle/llmhub-desktop/releases).
2. Extract the archive:
   ```bash
   tar -xzf llmhub-linux-x86_64.tar.gz
   ```
3. Run:
   ```bash
   ./llmhub/bin/llmhub
   ```

### AppImage
1. Download `llmhub-linux-x86_64.AppImage` from [Releases](https://github.com/radiocycle/llmhub-desktop/releases).
2. Make it executable:
   ```bash
   chmod +x llmhub-linux-x86_64.AppImage
   ./llmhub-linux-x86_64.AppImage
   ```

---

## Building from Source

Requirements:
- JDK 17 or higher
- GTK 3 (`libgtk-3-dev`)

Run:
```bash
./gradlew run
```

To package a standalone distributable:
```bash
./gradlew createDistributable
```
The output will be in `build/compose/binaries/main/app/llmhub/`.

---

## License

Apache-2.0
