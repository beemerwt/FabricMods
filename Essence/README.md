# Essence

**Essence** is a lightweight, modern Essentials-style mod for Fabric servers.  
It provides all the quality-of-life utilities you expect built for performance, stability, and modded compatibility.

---

## 🪶 Overview

Essence is designed to be the spiritual successor to the classic *EssentialsX* plugin, rebuilt from the ground up for Fabric.  
It focuses on simplicity, persistence, and integration with modern systems like the Fabric Permissions API and SQLite storage.

- Fully server-side — no client mod required.  
- Safe, transactional SQLite database for player and location data.  
- Integrated permission handling with automatic fallback to vanilla op levels.  
- Command-based player utilities that work even for offline players.

---

## ✨ Features

- **Homes & Warps** – Easily set, delete, and teleport to named locations.
- **Spawn Command** – Return to the world spawn or teleport new players automatically.
- **TPA System** – Request, accept, and deny teleportations between players.
- **Jails** – Temporarily or permanently restrict player movement.
- **Mute & Ban Commands** – Optional administrative tools for moderation.
- **Offline Player Support** – Manage data for players who are not currently online.
- **SQLite Persistence** – Player and location data stored in WAL-enabled SQLite databases under `config/Essence/`.
- **Permissions Integration** – Supports LuckPerms or any mod implementing Fabric Permissions API.

---

## ⚙️ Commands and Permissions
> Essence can integrate with Fabric Permissions API or Luck Permissions (specified in the config)
> If unavailable, it falls back to standard vanilla op levels.

| Command | Description | Permission | Default |
|----------|--------------|-------------|----------|
| `/home [name]` | Teleport to a home | `essence.home` | ✅ |
| `/sethome [name]` | Set a home at your current location | `essence.sethome` | OP 1 |
| `/delhome [name]` | Delete a home | `essence.delhome` | OP 1 |
| `/warp <name>` | Teleport to a warp | `essence.warp` | ✅ |
| `/setwarp <name>` | Create a warp | `essence.setwarp` | OP 2 |
| `/delwarp <name>` | Remove a warp | `essence.delwarp` | OP 2 |
| `/spawn` | Teleport to the spawn point | `essence.spawn` | ✅ |
| `/tpa <player>` | Send a teleport request | `essence.tpa` | ✅ |
| `/tpaccept` | Accept an incoming teleport request | `essence.tpaccept` | ✅ |
| `/tpdeny` | Deny an incoming teleport request | `essence.tpdeny` | ✅ |
| `/jail <player> [name]` | Send a player to a jail | `essence.jail` | OP 1 |
| `/unjail <player>` | Release a player from jail | `essence.unjail` | OP 1 |
| `/setjail <name>` | Create a jail at your current location | `essence.setjail` | OP 3 |
| `/jailinfo <name>` | Display info about a specific jail | `essence.jail.view` | OP 1 |
| `/jaillist` | List all jails | `essence.jail.view` | OP 1 |
| `/mute <player>` | Prevent a player from chatting | `essence.mute` | OP 2 |
| `/unmute <player>` | Unmute a player | `essence.unmute` | OP 2 |
| `/ban <player>` | Permanently ban a player | `essence.ban` | OP 2 |
| `/tempban <player> <time> [reason]` | Temporarily ban a player | `essence.tempban` | OP 1 |

---

## 🛠️ Configuration

Configuration files are stored in `config/Essence/`.

| File | Purpose |
|------|----------|
| `config.json5` | Core mod configuration (permissions, defaults, behavior). |
| `locations.db` | SQLite database for homes, warps, jails, and spawn. |
| `players.db` | SQLite database for player records (names, UUIDs, timestamps). |
