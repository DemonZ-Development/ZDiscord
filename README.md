<div align="center">

<img src="images/banner.png" alt="ZDiscord" width="100%">

# ZDiscord

Discord integration for Minecraft servers.

[![Version](https://img.shields.io/badge/version-1.1.0-blue)](https://github.com/DemonZ-Development/ZDiscord/releases)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.20.4%2B-green)](https://papermc.io)
[![Platform](https://img.shields.io/badge/Paper%20%7C%20Folia%20%7C%20Spigot-orange)](https://papermc.io)
[![Java](https://img.shields.io/badge/Java-17%2B-red)](https://adoptium.net)
[![License](https://img.shields.io/badge/License-Apache%202.0-lightgrey)](LICENSE)

[Releases](https://github.com/DemonZ-Development/ZDiscord/releases) · [Wiki](https://github.com/DemonZ-Development/ZDiscord/wiki) · [Issues](https://github.com/DemonZ-Development/ZDiscord/issues)

</div>

---

## About

ZDiscord is a Minecraft plugin that bridges your Minecraft server and Discord guild. It provides chat synchronisation, server status, account linking, tickets, leaderboards, anti-raid, and a small set of utility modules.

## Features

- **Chat bridge** — Two-way synchronisation between Minecraft and Discord. Webhooks are used to display player heads as avatars.
- **Server status** — A single message in a Discord channel that auto-edits with player count, TPS, and memory.
- **Console streaming** — Server log lines forwarded to a Discord channel.
- **Account linking** — One-time codes link Discord and Minecraft accounts. Optionally enforced as link-to-join.
- **Staff chat** — `/sc` toggles a staff-only chat that is also bridged to a Discord channel.
- **Tickets** — Players open private support channels via a Discord button or `/ticket`.
- **Leaderboards** — Kills, deaths, and playtime ranked via `/leaderboard`.
- **Event messages** — Joins, quits, deaths, and advancements posted to a Discord channel.
- **Performance monitor** — TPS and memory usage tracked over time with configurable alerts.
- **Anti-raid** — Mass-join detection with optional automatic lockdown.
- **Command logger** — Watched and critical commands posted to a staff channel.
- **Voice status** — Linked players get a tab-list indicator while they are in a tracked Discord voice channel.
- **Reaction roles** — Map message reactions to Discord roles and in-game permissions.
- **Setup wizard** — `/setup` configures channels from Discord with dropdowns and buttons.

## Requirements

- Java 17 or newer
- Paper 1.20.4 or newer, Folia, or Spigot 1.20.4 or newer
- A Discord application with a bot token. Enable the **Server Members** and **Message Content** intents on the bot.

## Installation

1. Download `ZDiscord-1.1.0.jar` from the [Releases](https://github.com/DemonZ-Development/ZDiscord/releases) page.
2. Place the JAR in your server's `plugins/` directory.
3. Start the server to generate the default `config.yml` and `messages.yml`.
4. Open `plugins/ZDiscord/config.yml` and set the following:
   - `bot.token` — your bot token
   - `bot.guild-id` — your Discord server ID
   - `channels.chat` — the channel ID for chat bridge
5. Restart the server.
6. Run `/setup` in Discord to configure the remaining channels interactively.

## Configuration

All configuration lives in `plugins/ZDiscord/config.yml`. A summary of the major sections:

| Section | Purpose |
|---|---|
| `storage` | YAML or MySQL backend for persistent data |
| `bot` | Bot token, activity, and target guild |
| `channels` | Discord channel IDs for each module |
| `chat` | Chat bridge formatting and webhook options |
| `events` | Join, quit, death, and advancement messages |
| `status` | Server status embed |
| `linking` | Account linking, optional enforcement, and reward commands |
| `anti-raid` | Mass-join thresholds and lockdown behaviour |
| `performance` | TPS and memory alert thresholds |
| `command-logger` | Watched and critical commands |
| `staff-chat`, `voice-status` | Staff chat bridge and voice status indicator |

User-facing strings are in `messages.yml` and accept `&` colour codes and the `%prefix%` placeholder.

## Commands

### In-game

| Command | Permission | Description |
|---|---|---|
| `/zdiscord reload` | `zdiscord.admin` | Reload configuration |
| `/zdiscord status` | `zdiscord.admin` | Show bot and platform status |
| `/zdiscord link` | `zdiscord.link` | Generate a link code |
| `/zdiscord embed <title> <description>` | `zdiscord.embed` | Send a custom embed |
| `/zdiscord ticket <subject>` | `zdiscord.ticket` | Open a support ticket |
| `/zdiscord lockdown` | `zdiscord.admin` | Toggle anti-raid lockdown |
| `/zdiscord dump` | `zdiscord.admin` | Write a diagnostics file |
| `/discord` | `zdiscord.discord` | Show the Discord invite link |
| `/sc [message]` | `zdiscord.staffchat` | Send to staff chat (toggle with no message) |

### Discord slash commands

| Command | Description |
|---|---|
| `/status` | Server status |
| `/players` | Online players |
| `/tps` | Server performance |
| `/link <code>` | Link a Discord account to a Minecraft account |
| `/ticket <subject>` | Open a support ticket |
| `/leaderboard <stat>` | View kills, deaths, or playtime leaderboard |
| `/setup` | Open the setup wizard |

## Building

```bash
git clone https://github.com/DemonZ-Development/ZDiscord.git
cd ZDiscord
mvn clean package
```

The shaded JAR is written to `target/ZDiscord-1.1.0.jar`.

## License

This project is licensed under the Apache License 2.0 — see [LICENSE](LICENSE).
