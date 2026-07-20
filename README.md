# ClipTV Client Mod

Record and share your Minecraft highlights on **ClapCraft** — directly from the game.

---

## Supported loaders

| Loader | File | Notes |
|--------|------|-------|
| **Fabric** | `clapcraft-cliptv-fabric-1.0.0.jar` | Use with Fabric Loader ≥ 0.16 |
| **Quilt** | *(same Fabric jar)* | Quilt is Fabric-compatible — just drop the Fabric jar in |
| **NeoForge** | `clapcraft-cliptv-neoforge-1.0.0.jar` | NeoForge ≥ 21.4 |
| **Forge** | Coming soon | Open a ticket in Discord to request |
| **Vanilla / OptiFine** | Not supported | Mod loader required |

**Don't see your setup?  → Open a `#mod-support` ticket in Discord.**

---

## Installation

1. Install your mod loader (Fabric Loader / NeoForge).
2. Download the correct jar from the Discord `#cliptv-mod` channel.
3. Drop it into your `.minecraft/mods/` folder.
4. Launch Minecraft 1.21.4 and join **play.clapcraft.net**.

---

## In-game commands

| Command | What it does |
|---------|-------------|
| `/clip` | Saves the **last 120 seconds** of gameplay as a video clip |
| `/rec` | Toggles continuous recording on/off |

Saved files land in `.minecraft/cliptv/drafts/` as standard `.avi` video files.

---

## How the server detects the mod

When you join the ClapCraft server, the mod automatically sends a handshake on the `clapcraft:cliptv` channel.  The server plugin sees this and unlocks the **ClipTV GUI** for your account.

Without the mod, the GUI shows a prompt directing you to this channel.

---

## Building from source

Requires JDK 21 and internet access for first-time Gradle downloads.

```bash
# Fabric jar (works on Fabric + Quilt)
cd mods/clapcraft-cliptv
./gradlew :fabric:remapJar

# NeoForge jar
./gradlew :neoforge:remapJar
```

Output jars are in `fabric/build/libs/` and `neoforge/build/libs/`.

---

## Planned features

- [ ] Upload clips directly to the ClipTV feed from in-game
- [ ] Live streaming (server-side toggle)
- [ ] Forge support
- [ ] In-game draft manager GUI
