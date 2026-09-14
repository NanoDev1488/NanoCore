# NanoCore

**Multi-Core Minecraft Server Manager** — Velocity + Paper + BungeeCord

[![GitHub](https://img.shields.io/badge/GitHub-NanoDev1488%2FNanoCore-blue)](https://github.com/NanoDev1488/NanoCore)

## What is NanoCore?

NanoCore is a single `java -jar NanoCore.jar` that:
- **Downloads** Velocity, Paper (1.21.4, 1.21.11), BungeeCord automatically
- **Patches** every jar on download (removes bStats, legacy protocols, RCON, deprecated APIs)
- **Manages** all servers as subprocesses with auto-assigned ports and Aikar's JVM flags
- **Provides** an interactive console + full CLI mode
- **Auto-builds** pre-patched jars via GitHub Actions on every release

## Quick Start

```bash
# Download & patch all server jars (first run)
java -jar NanoCore.jar download

# Start all servers
java -jar NanoCore.jar start all

# Interactive console
java -jar NanoCore.jar
```

## CLI Commands

```
download              Download & patch all server jars
list / ls             Status of all servers
start [id|all]        Start server(s)
stop  [id|all]        Graceful stop
restart <id>          Restart server
send <id> <command>   Send command to server console
logs <id> [N]         Show last N log lines
ports                 Show auto-assigned ports
```

## Flags

```bash
java -jar NanoCore.jar --basedir /path/to/servers
java -jar NanoCore.jar --java /path/to/java
java -jar NanoCore.jar --basedir ~/NanoCore start all
```

## What gets patched

| Component | Removed | Why |
|---|---|---|
| Velocity | bStats telemetry | Outbound connections, CPU & thread overhead |
| Velocity | Legacy MC <1.7 protocol | Zero players use Minecraft 1.6 in 2025 |
| Velocity | GS4 Query handler | Deprecated game-query protocol |
| Velocity | Debug dump endpoint | Security + unnecessary on production |
| Paper | bStats | Telemetry threads & HTTP connections |
| Paper | Legacy Bukkit shims | 2012-era plugin compatibility layer |
| Paper | RCON server | Unused TCP port, attack surface |
| Paper | Deprecated API layer | Dead weight for modern plugins |
| BungeeCord | bStats | Telemetry |
| BungeeCord | Legacy ping protocol | Old protocol support |

## Server Structure

```
NanoCore/
  proxy/                      ← Velocity proxy (port 25565)
  servers/
    paper-1.21.4/             ← Paper 1.21.4 backend (port 25566)
    paper-1.21.11/            ← Paper 1.21.11 backend (port 25567)
    bungee-legacy/            ← BungeeCord legacy proxy (port 25568)
  ports.properties            ← Auto-assigned ports (persistent)
```

## Build from source

```bash
mkdir -p out
find src -name "*.java" | xargs javac -d out
echo "Main-Class: com.nanocore.NanoCoreMain" > manifest.txt
jar cfm NanoCore.jar manifest.txt -C out .
java -jar NanoCore.jar
```

## Per-server configuration

Each server directory can have a `nanocore.properties` file:
```properties
heap=2G
extra_flags=-Dfile.encoding=UTF-8
port=25570
```

## GitHub Actions CI

On every release, GitHub Actions automatically:
1. Builds `NanoCore.jar`
2. Downloads & patches all server jars
3. Publishes everything to GitHub Releases

Users running `java -jar NanoCore.jar download` get pre-patched jars from Releases automatically.

---

**GitHub:** https://github.com/NanoDev1488/NanoCore
